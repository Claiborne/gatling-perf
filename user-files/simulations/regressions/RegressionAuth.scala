package regressions

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

class RegressionAuth extends Simulation {

  val env = System.getProperty("env")
  val envConf = ConfigFactory.load("env")
  val baseURL = envConf.getString(s"$env.baseURL")
  val authUser= envConf.getString(s"$env.authUser")
  val authPassword = envConf.getString(s"$env.authPassword")

  val httpConf = http
    .baseURL(baseURL)
    .acceptHeader("application/json")
    .basicAuth(authUser, authPassword)
    .header("Content-Type", "application/json")

  object Authorizations {
    

    val usersAndCards = csv(s"regressions/$env-users-and-cards.csv").circular

    val auth = feed(usersAndCards)
      .exec(http("Authorization")
        .post("/simulate/authorization")
        .body(StringBody("""{ 
          "amount": "0.01",
          "mid": "130741424",
          "card_token": "${card_token}",
          "await_completion":false
        }"""))
        .check(jsonPath("$.transaction.response.code").is("0000")))
  }
  
  val performAuth = scenario("Authorization").exec(Authorizations.auth)

  setUp(
    performAuth.inject(constantUsersPerSec(30) during(15 minutes))
  ).protocols(httpConf)
}
