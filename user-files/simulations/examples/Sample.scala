package examples

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

class Sample extends Simulation {

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

  object Get {

    val users = exec(http("Get Users Index")
      .get("/users"))
  }
  
  val getUsers = scenario("Authorization").exec(Get.users)

  setUp(
    getUsers.inject(atOnceUsers(2))
  ).protocols(httpConf)
}
