package com

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Scheduler}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.cache.CacheActor
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

// ----- JSON Marshalling -----
final case class PutRequest(value: String)
object JsonFormats extends DefaultJsonProtocol {
  implicit val putRequestFormat: RootJsonFormat[PutRequest] = jsonFormat1(PutRequest)
}

// ----- Cache HTTP API -----
class CacheRoutes(
                   system: ActorSystem[_],
                   sharding: ClusterSharding
                 )(implicit timeout: Timeout, ec: ExecutionContext, scheduler: Scheduler)
  extends Directives {

  import JsonFormats._

  private val log = system.log

  val health: Route =
    path("health") {
      get {
        log.debug("Received health check request")
        complete(StatusCodes.OK)
      }
    }

  val cache: Route =
    pathPrefix("cache" / Segment) { key =>
      val entityRef: EntityRef[CacheActor.Command] = sharding.entityRefFor(CacheActor.TypeKey, key)

      concat(
        put {
          entity(as[PutRequest]) { body =>
            log.info(s"PUT request received for key: $key with value: ${body.value}")
            entityRef ! CacheActor.Put(key, body.value, system.ignoreRef)
            complete(StatusCodes.OK -> s"Put request for key=$key stored")
          }
        },
        get {
          log.info(s"GET request received for key: $key")
          val responseFut = entityRef.ask(replyTo => CacheActor.Get(key, replyTo))
          onSuccess(responseFut) {
            case CacheActor.ValueFound(value) =>
              log.info(s"GET success for key=$key, value=$value")
              complete(StatusCodes.OK -> value)
            case CacheActor.ValueNotFound =>
              log.warn(s"GET key not found: $key")
              complete(StatusCodes.NotFound -> s"No value for key $key")
            case unexpected =>
              log.error(s"Unexpected GET response for key=$key: $unexpected")
              complete(StatusCodes.InternalServerError)
          }
        },
        delete {
          log.info(s"DELETE request received for key: $key")
          entityRef ! CacheActor.Delete(key, system.ignoreRef)
          complete(StatusCodes.OK -> s"Delete request sent for key=$key")
        }
      )
    }

  val routes: Route = health ~ cache
}

object AkkaHttpTypedClient {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("application.conf")

    implicit val system: ActorSystem[Any] = ActorSystem(Behaviors.empty[Any], "cluster-test", config)
    implicit val ec: ExecutionContext = system.executionContext
    implicit val scheduler: Scheduler = system.scheduler
    implicit val timeout: Timeout = 5.seconds
    val log = system.log

    log.info("Starting Akka HTTP Cluster Client...")

    val sharding = ClusterSharding(system)

    log.info("Initializing cluster sharding for CacheActor...")
    sharding.init(
      akka.cluster.sharding.typed.scaladsl.Entity(CacheActor.TypeKey)(_ => CacheActor())
    )
    log.info("Cluster sharding for CacheActor initialized.")

    val routes = new CacheRoutes(system, sharding)

    Http().newServerAt("0.0.0.0", 8080).bind(routes.routes)
    log.info("🚀 Server started on http://localhost:8080")

    Await.result(system.whenTerminated, Duration.Inf)
  }
}