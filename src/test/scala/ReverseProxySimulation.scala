
import io.gatling.core.Predef.*
import io.gatling.http.Predef.*

import scala.concurrent.duration.*

/**
  * Start [[akkahttp.ReverseProxy]]
  * Run this simulation from cmd shell:
  * sbt 'Gatling/testOnly ReverseProxySimulation'
  * or from sbt shell:
  * Gatling/testOnly ReverseProxySimulation
  */
class ReverseProxySimulation extends Simulation {
  val baseUrl = "http://127.0.0.1:8080"

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .userAgentHeader("Gatling")

  val scn = scenario("GatlingLocalClient")
    .exec(session => session.set("correlationId", 1))
    .repeat(10) {
      exec(
        http("Local Mode Request")
          .get("/")
          .header("Host", "local")
          .header("X-Correlation-ID", session => s"load-${session.userId}-${session("correlationId").as[Int]}")
          .check(status.is(200))
          .check(status.saveAs("responseStatus"))
          .check(header("X-Correlation-ID").saveAs("responseCorrelationId"))
      )
        .exec(session => {
          println(s"Got: ${session.status} response with HTTP status: ${session("responseStatus").as[String]} for id: ${session("responseCorrelationId").as[String]}")
          session
        })
        .exec(session => session.set("correlationId", session("correlationId").as[Int] + 1))
    }

  setUp(
    scn.inject(
      atOnceUsers(10),
      rampUsers(50).during(30.seconds)
    )
  ).protocols(httpProtocol)
}