package com

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.cache.{CacheActor, CacheService}
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

// ----- JSON Marshalling -----
final case class PutRequest(value: String)
object JsonFormats extends DefaultJsonProtocol {
  implicit val putRequestFormat: RootJsonFormat[PutRequest] = jsonFormat1(PutRequest)
}

// ----- Cache HTTP API -----
class CacheRoutes(
                   system: ActorSystem[_],
                   replySink: ActorRef[CacheActor.Response],
                   cacheLookup: () => Future[Option[ActorRef[CacheActor.Command]]]
                 )(implicit timeout: Timeout, ec: ExecutionContext, scheduler: Scheduler) extends Directives {

  import JsonFormats._

  val health: Route =
    path("health") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val cache: Route =
    pathPrefix("cache" / Segment) { key =>
      concat(
        put {
          entity(as[PutRequest]) { body =>
            onSuccess(cacheLookup()) {
              case Some(cacheActor) =>
                cacheActor ! CacheActor.Put(key, body.value, replySink)
                complete(StatusCodes.OK -> s"Put request for key=$key stored")
              case None =>
                complete(StatusCodes.ServiceUnavailable -> "Cache service unavailable")
            }
          }
        },
        get {
          onSuccess(cacheLookup()) {
            case Some(cacheActor) =>
              val responseFut = cacheActor.ask(replyTo => CacheActor.Get(key, replyTo))
              onSuccess(responseFut) {
                case CacheActor.ValueFound(value) =>
                  complete(StatusCodes.OK -> value)
                case CacheActor.ValueNotFound =>
                  complete(StatusCodes.NotFound -> s"No value for key $key")
                case _ =>
                  complete(StatusCodes.InternalServerError)
              }
            case None =>
              complete(StatusCodes.ServiceUnavailable -> "Cache service unavailable")
          }
        },
        delete {
          onSuccess(cacheLookup()) {
            case Some(cacheActor) =>
              cacheActor ! CacheActor.Delete(key, replySink)
              complete(StatusCodes.OK -> s"Delete request sent for key=$key")
            case None =>
              complete(StatusCodes.ServiceUnavailable -> "Cache service unavailable")
          }
        }
      )
    }

  val routes: Route = health ~ cache
}

// ----- Server Startup -----
object AkkaHttpTypedClient {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("application.conf")

    implicit val system: ActorSystem[Any] = ActorSystem(Behaviors.empty[Any], "cluster-test", config)
    implicit val ec: ExecutionContext = system.executionContext
    implicit val scheduler: Scheduler = system.scheduler
    implicit val timeout: Timeout = 5.seconds

    val replySink: ActorRef[CacheActor.Response] =
      system.systemActorOf(Behaviors.ignore, "reply-sink")

    val cacheLookup: () => Future[Option[ActorRef[CacheActor.Command]]] = () =>
      system.receptionist
        .ask[Receptionist.Listing](Receptionist.Find(CacheService.key, _))
        .map(_.serviceInstances(CacheService.key).headOption)

    val api = new CacheRoutes(system, replySink, cacheLookup)

    Http().newServerAt("0.0.0.0", 8080).bind(api.routes)
    println("HTTP server running at http://localhost:8080")

    Await.result(system.whenTerminated, Duration.Inf)
  }
}
