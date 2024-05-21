package alpakka.dynamodb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import scala.jdk.javaapi.FutureConverters;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

/**
 * Setup/run {@link alpakka.dynamodb.DynamoDBEcho} on localStack container
 * <p>
 * Running this example against AWS:
 * Looks as if there is a way to delete a DB instance via the SDK:
 * https://docs.aws.amazon.com/code-library/latest/ug/rds_example_rds_DeleteDBInstance_section.html
 * However, getting the `dbInstanceIdentifier` via SDK is not straightforward
 * Therefore, we only run against localStack for now in order to avoid dangling resources
 * <p>
 * Doc:
 * https://pekko.apache.org/docs/pekko-connectors/current/dynamodb.html#aws-dynamodb
 * https://community.aws/content/2dxWQAZsdc3dk5uCILAmNqEME2e/testing-dynamodb-interactions-in-spring-boot-using-localstack-and-testcontainers?lang=en
 */
@Testcontainers
public class DynamoDBEchoIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBEchoIT.class);

    @Container
    public static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.3"))
            .withCopyFileToContainer(MountableFile.forClasspathResource("/localstack/init_dynamodb.sh", 700), "/etc/localstack/init/ready.d/init_dynamodb.sh")
            .withServices(DYNAMODB)
            .waitingFor(Wait.forLogMessage(".*Executed init_dynamodb.sh.*", 1));


    @BeforeAll
    public static void beforeAll() {
        localStack.start();
        LOGGER.info("LocalStack container started on host address: {}", localStack.getEndpoint());
    }

    @Test
    public void testLocal() throws ExecutionException, InterruptedException {
        DynamoDBEcho dynamoDBEcho = new DynamoDBEcho(localStack.getEndpointOverride(DYNAMODB), localStack.getAccessKey(), localStack.getSecretKey(), localStack.getRegion());
        int noOfItemsEven = 10;

        CompletionStage<Object> result = FutureConverters.asJava(dynamoDBEcho.run(noOfItemsEven));
        assertThat(result.toCompletableFuture().get()).isEqualTo(noOfItemsEven / 2);
    }
}

