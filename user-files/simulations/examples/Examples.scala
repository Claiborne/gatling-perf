package examples

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.concurrent.ThreadLocalRandom // used below ThreadLocalRandom.current.nextInt(500)
import com.typesafe.config.ConfigFactory

// to run $ cd bin/ && JAVA_OPTS="-Denv=local" sh gatling.sh -rf results/local/17-2-0/

// other cool things are described in this article:
// https://medium.com/@vcomposieux/load-testing-gatling-tips-tricks-47e829e5d449

class Example extends Simulation {

  // all these vars are getting valus from conf/env.conf, which is something custom I've set setup
  // thus you may want to hard-code these values
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
      .get("/transactions?count=100&state=ALL")) // a simple GET
  }

  object Users {

    val createUserAndCard = exec(http("Create User")
      .post("/users")
      .body(StringBody("""{ 
          "first_name": "Gatling User" 
        }""")) // this is how you construct a JSON request body
      .check(jsonPath("$.token").saveAs("user_token"))) // save specific element of JSON response
      .exec(http("Create Card") // chain together a sequential action, here a POST to /cards API
      .post("/cards")
      .body(StringBody("""{ 
          "user_token":"${user_token}",
          "card_product_token"; "3ca400ab-fe41-4f3b-9576-adea19661b56" 
        }""")) // use specific element of previous JSON response
      .check(jsonPath("$").saveAs("RESPONSE_DATA")))  // output a JSON response
      .exec( session => {
        println( "API Response Body:" )
        println( session( "RESPONSE_DATA" ).as[String] )
        session
      })
  }

  object Authorizations {

    val usersAndCards = csv(s"examples/$env-users-and-cards.csv").random

    val jitAuthAndClear = feed(usersAndCards)
      .exec(http("Authorization (JIT)")
        .post("/simulate/authorization")
        .body(StringBody("""{ 
          "amount": "0.3",
          "mid": "130741424",
          "card_token": "${card_token}",
          "await_completion":false
        }"""))
        .check(jsonPath("$.transaction.response.code").is("0000")))
      .exec(http("Clearing")
        .post("/simulate/advanced/clearing")
        .body(StringBody("""{ 
          "amount": "0.3",
          "mid": "130741424",
          "card_token": "${card_token}"
        }"""))
        .check(jsonPath("$.transaction.response.code").is("0000")))
 }

  object Examples {

    val somethingThatRepeats = repeat(3, "i") {
      exec(http("Get Users ${i}")
        .get("/users"))
    }

    val postWithRandomVar = exec(session => session.set("randomN",ThreadLocalRandom.current.nextInt(500)))
      .exec(http("Create User")
        .post("/users")
        .body(StringBody("""{ "first_name": "Gatling User ${randomN}" }""")))
  }

  val t1 = scenario("Create Users and Cards").exec(Users.createUserAndCard)
  val getTransaction = scenario("GET transactions Thread").exec(Transactions.getIndex)

  setUp(
    t1.inject(atOnceUsers(2))
    //tg1.inject(atOnceUsers(2))
    //postAuths.inject(       atOnceUsers(2) during(5 seconds)),
    //getTransaction.inject(  atOnceUsers(2) during(5 seconds))
  ).protocols(httpConf)
}
