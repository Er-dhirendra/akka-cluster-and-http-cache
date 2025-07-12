
package com.cluster

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.cache.{CacheActor, CacheService}
import com.repository.InMemoryCacheRepository
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ClusterApplication {

  private object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      context.spawn(ClusterLogger(), "ClusterLogger")
      val cacheActor: ActorRef[CacheActor.Command] =
        context.spawn(CacheActor(new InMemoryCacheRepository), "CacheActor")
      context.system.receptionist ! Receptionist.Register(CacheService.key, cacheActor)
      Behaviors.same
    }
  }

  def main(args: Array[String]): Unit = {
    val port =
      if (args.nonEmpty) args(0).toInt
      else throw new IllegalArgumentException("Port number must be passed as a command-line argument")

    val config = ConfigFactory.parseString(
      s"akka.remote.artery.canonical.port = $port"
    ).withFallback(ConfigFactory.load("application.conf"))

    val system = ActorSystem[Nothing](RootBehavior(), "cluster-test", config)

    Await.result(system.whenTerminated, Duration.Inf)
  }
}

