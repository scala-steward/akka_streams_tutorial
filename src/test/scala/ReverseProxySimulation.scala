
import io.gatling.core.Predef.*
import io.gatling.http.Predef.*

import scala.concurrent.duration.*

/**
  * Start [[akkahttp.ReverseProxy]]
  * Run this Simulation with:
  * sbt 'Gatling/testOnly ReverseProxySimulation'
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
          .header("X-Correlation-ID", session => s"1-${session("correlationId").as[Int]}")
          .check(status.is(200))
          .check(header("X-Correlation-ID").saveAs("responseCorrelationId"))
      )
        .exec(session => {
          println(s"Got response for id: ${session("responseCorrelationId").as[String]}")
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