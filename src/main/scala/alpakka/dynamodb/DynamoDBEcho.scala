package alpakka.dynamodb


import com.github.pjfanning.pekkohttpspi.PekkoHttpClient
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.dynamodb.scaladsl.DynamoDb
import org.apache.pekko.stream.scaladsl.{FlowWithContext, Sink, Source}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy
import software.amazon.awssdk.core.retry.conditions.RetryCondition
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

import java.net.URI
import java.util
import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try


class DynamoDBEcho(urlWithMappedPort: URI, accessKey: String, secretKey: String, region: String) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  implicit val system = ActorSystem("DynamoDBEcho")
  implicit val executionContext = system.dispatcher

  private val testTableName = "testTable"

  val credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
  implicit val client: DynamoDbAsyncClient = createAsyncClient()

  def run(noOfItems: Int): Future[Int] = {
    for {
      _ <- createTable()
      _ <- writeItems(noOfItems)
      result <- readItems()
    } yield result
  }

  // Create a table and read description of this table
  private def createTable() = {
    val source: Source[DescribeTableResponse, NotUsed] = Source
      .single(createTableRequest())
      .via(DynamoDb.flow(parallelism = 1))
      .map(response => DescribeTableRequest.builder().tableName(response.tableDescription.tableName).build())
      .via(DynamoDb.flow(parallelism = 1))

    source
      .map(descTableResponse => logger.info(s"Successfully created table: ${descTableResponse.table.tableName}"))
      .runWith(Sink.ignore)
  }

  private def createTableRequest(): CreateTableRequest = {
    val attributeDefinitions = util.Arrays.asList(
      AttributeDefinition.builder()
        .attributeName("Id")
        .attributeType(ScalarAttributeType.S)
        .build()
    )

    val keySchema = util.Arrays.asList(
      KeySchemaElement.builder()
        .attributeName("Id")
        .keyType(KeyType.HASH)
        .build()
    )

    CreateTableRequest.builder()
      .tableName(testTableName)
      .keySchema(keySchema)
      .attributeDefinitions(attributeDefinitions)
      .provisionedThroughput(ProvisionedThroughput.builder()
        .readCapacityUnits(5L)
        .writeCapacityUnits(5L)
        .build())
      .build()
  }


  private def writeItems(noOfItems: Int) = {
    logger.info(s"About to write $noOfItems items...")

    case class RequestContext(tableName: String, requestId: String)

    val sourceWrite = Source(1 to noOfItems)
      .map(item => {
        val requestId = UUID.randomUUID().toString
        val request = PutItemRequest.builder().tableName(testTableName).item(Map(
          "Id" -> AttributeValue.builder().s(item.toString).build(),
          "att1" -> AttributeValue.builder().s(s"att1-$item").build(),
          "att2" -> AttributeValue.builder().s(s"att2-$item").build()
        ).asJava).build()
        (request, RequestContext(testTableName, requestId))
      })


    val flowWrite: FlowWithContext[PutItemRequest, RequestContext, Try[PutItemResponse], RequestContext, NotUsed] =
      DynamoDb.flowWithContext(parallelism = 1)

    sourceWrite
      .via(flowWrite)
      .map((response: (Try[PutItemResponse], RequestContext)) => {
        val status = response._1.get.sdkHttpResponse().statusCode()
        logger.info(s"Successfully written item. Http response: $status")

      })
      .runWith(Sink.ignore)
  }

  private def readItems() = {
    logger.info(s"About to read items...")

    // This hangs when no table is available
    val scanRequest = ScanRequest.builder().tableName(testTableName).build()
    val scanPageInFlow: Source[ScanResponse, NotUsed] =
      Source
        .single(scanRequest)
        .via(DynamoDb.flowPaginated())

    scanPageInFlow.map { (response: ScanResponse) => {
      val count = response.scannedCount()
      logger.info(s"Successfully read $count items")
      response.items().forEach(item => logger.info(s"Item: $item"))
      response.items().size()
    }

    }.runWith(Sink.head)
  }

  private def createAsyncClient() = {
    val client = DynamoDbAsyncClient
      .builder()
      .endpointOverride(urlWithMappedPort)
      .region(Region.of(region))
      .credentialsProvider(credentialsProvider)
      .httpClient(PekkoHttpClient.builder().withActorSystem(system).build())
      // https://pekko.apache.org/docs/pekko-connectors/current/aws-shared-configuration.html
      // https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/core/retry/RetryPolicy.html
      .overrideConfiguration(
        ClientOverrideConfiguration
          .builder()
          .retryPolicy(
            RetryPolicy.builder
              .backoffStrategy(BackoffStrategy.defaultStrategy)
              .throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy)
              .numRetries(SdkDefaultRetrySetting.defaultMaxAttempts)
              .retryCondition(RetryCondition.defaultRetryCondition)
              .build)
          .build())
      .build()
    system.registerOnTermination(client.close())
    client
  }
}

