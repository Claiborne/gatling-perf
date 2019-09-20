package regressions

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

class RegressionAPI extends Simulation {

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

    val users = csv(s"regressions/$env-users-and-cards.csv").random
 
    val getIndexByUserToken = feed(users)
      .exec(http("Get Transactions Index")
      .get("/transactions?user_token=${user_token}"))
  }

  object Cards {

    val usersOnly = csv(s"regressions/$env-users-only.csv").random
    val usersAndCards = csv(s"regressions/$env-users-and-cards.csv").random

    val createCard = feed(usersOnly)
      .exec(http("Create Card")
        .post("/cards")
        .body(StringBody("""{ 
          "user_token": "${user_token}",
          "card_product_token":"b463636-6198-4730-b862-850fe1be2a79"
        }"""))
      )

    val showPan = feed(usersAndCards)
      .exec(http("Show Pan")
        .get("/cards/${card_token}/showpan"))

    val activate = feed(usersAndCards)
      .exec(http("Activate Card")
        .post("/cardtransitions")
        .body(StringBody("""{ 
          "card_token": "${card_token}",
          "channel":"API",
          "state":"ACTIVE"
        }"""))
      )

    val setPIN = feed(usersAndCards)
      .exec(http("Set PIN controltoken")
        .post("/pins/controltoken")
        .body(StringBody("""{ "card_token": "${card_token}" }"""))
        .check(jsonPath("$.control_token").saveAs("pin_control_token")))
      .pause(1 second)
      .exec(http("Set PIN actual")
        .put("/pins")
        .body(StringBody("""{ 
          "control_token": "${pin_control_token}",
          "pin": "7293"
        }""")))
  }

  object Users {

    val usersOnly = csv(s"regressions/$env-users-only.csv").random

    val createUser = exec(http("Create User")
      .post("/users")
      .body(StringBody("""{ 
        "first_name": "QE",
        "last_name": "User",
        "notes": "QE user created by Gatling test",
        "address1": "123 Fake St.",
        "city": "Oakland",
        "state": "CA",
        "zip": "94607",
        "birth_date": "1987-12-17"
      }""")))

    val modifyUser = feed(usersOnly)
      .exec(http("Modify User")
        .put("/users/${user_token}")
        .body(StringBody("""{ 
          "notes": "User modified by QE Gatling"
        }""")))
  }

  object KYC {

    val usersAndCards = csv(s"regressions/$env-users-only.csv").random

    val manualOverride = feed(usersAndCards)
      .exec(http("KYC Override")
        .post("/kyc")
        .body(StringBody("""{ 
          "user_token": "${user_token}",
          "manual_override": "true",
          "notes": "QE gatling test",
          "reference_id": "237492374973"
        }""")))
  }

  val getTransactionsIndex  = scenario("GET Transactions").exec(Transactions.getIndexByUserToken)
  val postCards             = scenario("POST Cards").exec(Cards.createCard)
  val getCardsShowPan       = scenario("GET Cards Show Pan").exec(Cards.showPan)
  val postUsers             = scenario("POST Users").exec(Users.createUser)
  val putUsers              = scenario("PUT Users").exec(Users.modifyUser)
  val performKYC            = scenario("KYC Override").exec(KYC.manualOverride)
  val setPINonCards         = scenario("Set PIN").exec(Cards.setPIN)
  val acivateCard           = scenario("Activate Card").exec(Cards.activate)

  setUp(
    getTransactionsIndex.inject( constantUsersPerSec(1)  during(15 minutes)),
    postCards.inject(            constantUsersPerSec(6)  during(15 minutes)),
    getCardsShowPan.inject(      constantUsersPerSec(30) during(15 minutes)),
    postUsers.inject(            constantUsersPerSec(10) during(15 minutes)),
    putUsers.inject(             constantUsersPerSec(6)  during(15 minutes)),
    performKYC.inject(           constantUsersPerSec(3)  during(15 minutes)),
    setPINonCards.inject(        constantUsersPerSec(2)  during(15 minutes)),
    acivateCard.inject(          constantUsersPerSec(2)  during(15 minutes))
  ).protocols(httpConf)
}
