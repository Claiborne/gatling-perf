package setup

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import java.io.File
import java.io.PrintWriter
import java.io.FileOutputStream

class CreateUsersAndCards extends Simulation {

  val env = System.getProperty("env")
  val pathInData = System.getProperty("path")
  val envConf = ConfigFactory.load("env")
  val baseURL = envConf.getString(s"$env.baseURL")
  val authUser= envConf.getString(s"$env.authUser")
  val authPassword = envConf.getString(s"$env.authPassword")

  val httpConf = http
    .baseURL(baseURL)
    .acceptHeader("application/json")
    .basicAuth(authUser, authPassword)
    .header("Content-Type", "application/json")

  val gatewayFunding = "2d203h0h4230h4" 
  val cardProduct = "2d34234223r2d"
  val contrivedUser = "user_123"
  val contrivedPass = "pass_123"
  val csvPath = getClass.getResource("/data").getPath
  val csvFilePath = csvPath+"/"+pathInData

  before {
    val directory = new File(String.valueOf(csvFilePath))
    if(!directory.exists()){
      directory.mkdir()
    }

    val writer_users_and_cards = new PrintWriter(new FileOutputStream(new File(csvFilePath+"/"+env+"-users-and-cards.csv"), false))
    writer_users_and_cards.println("user_token,card_token")
    writer_users_and_cards.close()

    val writer_users_only = new PrintWriter(new FileOutputStream(new File(csvFilePath+"/"+env+"-users-only.csv"), false))
    writer_users_only.println("user_token")
    writer_users_only.close()
  }

  object Create {

    val cardProductandDependencies = exec(session => session.set("contrivedUser", contrivedUser))
      .exec(session => session.set("contrivedPass", contrivedPass))

      .exec(http("Create Auth User for JIT Gateway")
      .post("/authentications/users")
      .body(StringBody("""{ 
        "roles": ["admin"],
        "username": "${contrivedUser}",
        "password": "${contrivedPass}",
        "active": "true"
      }""")).check(status.in(201, 409)))
      .exec(session => session.set("gatewayFunding", gatewayFunding))

      .exec(http("Create Program Gateway Funding Source")
      .post("/fundingsources/programgateway")
      .body(StringBody("""{ 
        "token": "${gatewayFunding}",
        "url": "http://localhost:8080/v3/simulate/programgateway",
        "active": "true",
        "name": "Gatling PGFS JIT v2",
        "basic_auth_username": "${contrivedUser}",
        "basic_auth_password": "${contrivedPass}"
        }""")))
      .exec(session => session.set("cardProduct", cardProduct))


      .exec(http("Create JIT v2 Card Product")
      .post("/cardproducts")
      .body(StringBody("""{ 
        "token": "${cardProduct}",
        "name": "Gatling JIT v2",
        "active": "true",
        "start_date": "2014-10-10",
        "end_date": "2025-01-31",
        "config": {
          "card_life_cycle": {
            "activate_upon_issue": "true"
          },
          "jit_funding": {
            "programgateway_funding_source": {
              "enabled": "true",
              "funding_source_token": "${gatewayFunding}"
            }
          }
        }
      }""")).check(status.in(201, 409)))

    val usersAndCards = exec(http("Create User And Card")
      .post("/users")
      .body(StringBody("""{ 
        "first_name": "Gatling",
        "address1": "123 Fake St",
        "city": "Oakland",
        "state": "CA",
        "postal_code": "94610",
         "ssn": "555555555"
      }"""))
      .check(jsonPath("$.token").saveAs("userToken")))
      .exec(session => session.set("cardProduct", cardProduct))

      .exec(http("Create Card")
      .post("/cards")
      .body(StringBody("""{ 
        "user_token": "${userToken}",
        "card_product_token": "${cardProduct}" 
      }"""))
      .check(jsonPath("$.token").saveAs("cardToken")))
      .exec(session => session.set("csvFilePath", csvFilePath))

      .exec { session => 
        val writer_users_and_cards = new PrintWriter(new FileOutputStream(new File(csvFilePath+"/"+env+"-users-and-cards.csv"), true))
        writer_users_and_cards.print(session("userToken").as[String])
        writer_users_and_cards.print(",")
        writer_users_and_cards.println(session("cardToken").as[String])
        writer_users_and_cards.close()
        session
       }

    val usersOnly = exec(http("Create User Only")
      .post("/users")
      .body(StringBody("""{ 
        "first_name": "Gatling",
        "address1": "123 Fake St",
        "city": "Oakland",
        "state": "CA",
        "postal_code": "94610",
         "ssn": "555555555"
      }"""))
      .check(jsonPath("$.token").saveAs("userOnlyToken")))

      .exec { session => 
        val writer_users_only = new PrintWriter(new FileOutputStream(new File(csvFilePath+"/"+env+"-users-only.csv"), true))
        writer_users_only.println(session("userOnlyToken").as[String])
        writer_users_only.close()
        session
       }
  }

  val buildUserOnlyCSV =  scenario("Create Users Only").exec(Create.usersOnly)
  val createCardProduct = scenario("Create Card Product").exec(Create.cardProductandDependencies)
  val buildUserCardCSV =  scenario("Create Users and Cards").exec(Create.usersAndCards)
  
  setUp(
    createCardProduct.inject(atOnceUsers(1)),
    buildUserOnlyCSV.inject(constantUsersPerSec(4) during(1 minutes)),
    buildUserCardCSV.inject(
      nothingFor(15 seconds), // bad hack force createCardProduct act as a before all hook
      constantUsersPerSec(4) during(1 minutes))
  ).protocols(httpConf)
}
