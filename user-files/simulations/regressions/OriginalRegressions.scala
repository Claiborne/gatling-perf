package regressions

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

class OriginalRegressions extends Simulation {

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

  object Transactions {

    val getIndex = exec(http("Transactions Index")
      .get("/transactions?count=100&state=ALL"))
  }

  object Authorizations {

    val usersAndCards = csv(s"regressions/$env-users-and-cards.csv").random
 
    val jitAuthAndClear = feed(usersAndCards)
      .exec(http("Authorization (JIT)")
        .post("/simulate/authorization")
        .body(StringBody("""{ 
          "amount": "0.01",
          "mid": "130741424",
          "card_token": "${card_token}",
          "await_completion":false
        }"""))
        .check(jsonPath("$.transaction.response.code").is("0000")))
 }

  val getTransactionsIndex  = scenario("GET Transactions Thread").exec(Transactions.getIndex)
  val postAuthsandClearings = scenario("POST Authorization Thread").exec(Authorizations.jitAuthAndClear)
  setUp(
    getTransactionsIndex.inject(   constantUsersPerSec(1) during(5 minutes)),
    postAuthsandClearings.inject(  constantUsersPerSec(1) during(5 minutes))
  ).protocols(httpConf)
}
