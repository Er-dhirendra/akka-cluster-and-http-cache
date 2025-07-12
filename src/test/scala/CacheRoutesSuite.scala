import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import com.CacheRoutes
import com.cache.CacheActor
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future
import scala.concurrent.duration._
class CacheRoutesSuite
  extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest {

  val testKit = ActorTestKit()
  implicit val scheduler: Scheduler = testKit.system.scheduler
  implicit val timeout: Timeout = 3.seconds

  val replyProbe: TestProbe[CacheActor.Response] = testKit.createTestProbe[CacheActor.Response]()
  val commandProbe: TestProbe[CacheActor.Command] = testKit.createTestProbe[CacheActor.Command]()

  val cacheLookup: () => Future[Option[ActorRef[CacheActor.Command]]] = () => Future.successful(Some(commandProbe.ref))

  val routes: Route = new CacheRoutes(testKit.system, replyProbe.ref, cacheLookup).routes

  "CacheRoutes" should {

    "respond to health check" in {
      Get("/health") ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "handle PUT request" in {
      val json = """{"value": "test-value"}"""
      Put("/cache/test-key")
        .withEntity(ContentTypes.`application/json`, json) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("test-key")
      }
    }

    "handle GET with value found" in {
      val actor = testKit.spawn(Behaviors.receiveMessage[CacheActor.Command] {
        case CacheActor.Get("my-key", replyTo) =>
          replyTo ! CacheActor.ValueFound("my-value")
          Behaviors.same
        case _ => Behaviors.unhandled
      })

      val testRoutes = new CacheRoutes(testKit.system, replyProbe.ref, () => Future.successful(Some(actor))).routes

      Get("/cache/my-key") ~> testRoutes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldEqual "my-value"
      }
    }

    "handle GET with value not found" in {
      val actor = testKit.spawn(Behaviors.receiveMessage[CacheActor.Command] {
        case CacheActor.Get("missing-key", replyTo) =>
          replyTo ! CacheActor.ValueNotFound
          Behaviors.same
        case _ => Behaviors.unhandled
      })

      val testRoutes = new CacheRoutes(testKit.system, replyProbe.ref, () => Future.successful(Some(actor))).routes

      Get("/cache/missing-key") ~> testRoutes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "handle DELETE request" in {
      Delete("/cache/test-key") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("test-key")
      }
    }

    "return 503 if cache actor is not available" in {
      val unavailableLookup: () => Future[Option[ActorRef[CacheActor.Command]]] = () => Future.successful(None)
      val fallbackRoutes = new CacheRoutes(testKit.system, replyProbe.ref, unavailableLookup).routes

      Get("/cache/any-key") ~> fallbackRoutes ~> check {
        status shouldBe StatusCodes.ServiceUnavailable
      }
    }
  }
}
