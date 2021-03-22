package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.test._
import play.api.test.Helpers._

class ResControllerSpec extends PlaySpec with GuiceOneServerPerSuite  {

  val wsClient              = app.injector.instanceOf[WSClient]
  val myPublicAddress       = s"localhost:$port"
  val testPaymentGatewayURL = s"http://$myPublicAddress"

  val testJson = Json.parse(
    """
      |{
      |"graph": {
      |"components": [
      |{
      |"id": "app",
      |"own_state": "no_data",
      |"derived_state": "no_data",
      |"check_states": {
      |"CPU load": "no_data",
      |"RAM usage": "no_data"
      |},
      |"depends_on": ["db"]
      |},
      |{
      |"id": "db",
      |"own_state": "no_data",
      |"derived_state": "no_data",
      |"check_states": {
      |"CPU load": "no_data",
      |"RAM usage": "no_data"
      |},
      |"dependency_of": ["app"]
      |}
      |]
      |}
      |}
    """.stripMargin)

  "ResController Upload components" should {

    "accept valid JSON" in {
      val response = await(wsClient.url(s"$testPaymentGatewayURL/api/topology").post(testJson))
      response.status mustBe OK
    }

    /*"render the index page from the application" in {
      val controller = inject[ResController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }

    "render the index page from the router" in {
      val request = FakeRequest(GET, "/")
      val home = route(app, request).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }*/
  }

  "ResController GET components" should {

    "return valid JSON result" in {
      await(wsClient.url(s"$testPaymentGatewayURL/api/topology").post(testJson))
      val response = await(wsClient.url(s"$testPaymentGatewayURL/api/topology").get())

      response.status mustBe OK
      response.contentType mustBe "application/json"

      val json = Json.parse(response.bodyAsBytes.utf8String)

      (json \ "graph" \ "components").as[JsArray].value.contains(Json.parse(
        """
          |{
          |"id": "app",
          |"own_state": "no_data",
          |"derived_state": "no_data",
          |"check_states": {
          |"CPU load": "no_data",
          |"RAM usage": "no_data"
          |},
          |"depends_on": ["db"]
          |}
        """.stripMargin))

      (json \ "graph" \ "components").as[JsArray].value.contains(Json.parse(
        """
          |{
          |"id": "db",
          |"own_state": "no_data",
          |"derived_state": "no_data",
          |"check_states": {
          |"CPU load": "no_data",
          |"RAM usage": "no_data"
          |},
          |"dependency_of": ["app"]
          |}
        """.stripMargin))
    }
  }

  "ResController Upload && Send event && Get" should {

    "return valid JSON result" in {
      await(wsClient.url(s"$testPaymentGatewayURL/api/topology").post(testJson))
      val eventResponse1 = await(wsClient.url(s"$testPaymentGatewayURL/api/events").post(Json.parse(
        """
          |{
          |"timestamp": "1",
          |"component": "db",
          |"check_state": "CPU load",
          |"state": "warning"
          |}
        """.stripMargin)))
      eventResponse1.status mustBe OK

      val eventResponse2 = await(wsClient.url(s"$testPaymentGatewayURL/api/events").post(Json.parse(
        """
          |{
          |"timestamp": "2",
          |"component": "app",
          |"check_state": "CPU load",
          |"state": "clear"
          |}
        """.stripMargin)))

      eventResponse2.status mustBe OK

      val response = await(wsClient.url(s"$testPaymentGatewayURL/api/topology").get())

      response.status mustBe OK
      response.contentType mustBe "application/json"

      val json = Json.parse(response.bodyAsBytes.utf8String)

      (json \ "graph" \ "components").as[JsArray].value.contains(Json.parse(
        """
          |{
          |"id": "app",
          |"own_state": "clear",
          |"derived_state": "warning",
          |"check_states": {
          |"CPU load": "clear",
          |"RAM usage": "no_data"
          |},
          |"depends_on": ["db"]
          |}
        """.stripMargin))

      (json \ "graph" \ "components").as[JsArray].value.contains(Json.parse(
        """
          |{
          |"id": "db",
          |"own_state": "warning",
          |"derived_state": "warning",
          |"check_states": {
          |"CPU load": "warning",
          |"RAM usage": "no_data"
          |},
          |"dependency_of": ["app"]
          |}
        """.stripMargin))
    }
  }
}
