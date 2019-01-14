package akkahttp

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes, _}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import org.apache.commons.lang3.time.StopWatch

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * Inspired by:
  * https://discuss.lightbend.com/t/is-there-any-proper-way-to-gracefully-shutdown-source-queue/3086/3
  *
  * TODOs
  * Add issue: try with .complete() method in combination of Sink completion Future[Done], so that it works
  * Integrate FileEcho server for Http download?
  *
  * Doc difference to DownloaderRetry and to FileEcho
  *
  * @param uri
  * @param successPredicate
  */

object AkkaHttpClient extends App {

  val stopWatch = StopWatch.createStarted()
  // "http://httpstat.us/503"
  val client = new AkkaHttpClient("https://akka.io", resp => resp.status == StatusCodes.Accepted)
  (1 to 10).foreach(number => {
    client.submitBody(s"test $number!")
  })
  client.shutdown(2.minutes)
  println(s"Time passed: ${stopWatch.getTime} ms")
}



class AkkaHttpClient(uri: String, successPredicate: HttpResponse => Boolean) {
  var retryable = true
  var maxRetryAttempts = 3

  private val MAX_QUEUE_SIZE = Int.MaxValue
  private val RUN_FUTURE_FIRST_ATTEMPT = 1

  private val parsedUri = new URI(uri)

  //TODO
  private implicit val system: ActorSystem = ActorSystem()
  private implicit val materializer: ActorMaterializer = ActorMaterializer.create(system)
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private var terminated = false

  private val connectionPool = createCachedConnectionPool(parsedUri)
  private val sourceQueueAndDone = createSourceQueue(connectionPool)


  def submitBody(body: String,
                 method: HttpMethod = HttpMethods.POST,
                 contentType: ContentType.NonBinary = ContentTypes.`application/json`) {
    val request = HttpRequest(method, uri, entity = HttpEntity(contentType, body))

    val responseFuture: Future[HttpResponse] = queueRequest(request)
    if (retryable)
      retryFuture(responseFuture)
  }

  def shutdown(timeout: FiniteDuration) {
    sourceQueueAndDone._1.complete()
    try {
      // Does not awaits :(
      Await.result(sourceQueueAndDone._1.watchCompletion(), timeout)
    } catch {
      case _: Throwable =>
    } finally {
      terminated = true
      system.terminate()
    }
  }

  private def queueRequest(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    sourceQueueAndDone._1.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued => responsePromise.future
      case QueueOfferResult.Dropped => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed => Future.failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
    }
  }

  def retryFuture(target: Future[HttpResponse]): Unit = {
    retryFuture(target, RUN_FUTURE_FIRST_ATTEMPT)
  }

  private def retryFuture(target: Future[HttpResponse], attempt: Int): Future[HttpResponse] = {
    target.recoverWith {
      case _ =>
        if (!terminated && attempt <= maxRetryAttempts) {
          println("retrying..")
          retryFuture(target, attempt + 1)
        }
        else {
          println("failed (again)!")
          Future.failed(new Exception)
        }
    }
  }

  private def createCachedConnectionPool(uri: URI): Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), Http.HostConnectionPool] = {
    if (uri.getScheme == "http")
      Http().cachedHostConnectionPool[Promise[HttpResponse]](uri.getHost, if (uri.getPort == -1) 80 else uri.getPort)
    else
      Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]](uri.getHost, if (uri.getPort == -1) 443 else uri.getPort)
  }

  private def createSourceQueue(connectionPool: Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), Http.HostConnectionPool])
   = {

    //TODO Add Done stuff

    Source.queue[(HttpRequest, Promise[HttpResponse])](MAX_QUEUE_SIZE, OverflowStrategy.dropNew)
      .via(connectionPool)
      .toMat(Sink.foreach({
        case (Success(resp), p) =>
          if (successPredicate.apply(resp)) {
            println("completed!")
            p.success(resp)
          }
          else {
            println("really failed!")
            p.failure(new Exception("Result is not satisfied: " + resp.toString()))
          }
        case (Failure(e), p) => p.failure(e)
      }))(Keep.both)
      .run()
  }
}
