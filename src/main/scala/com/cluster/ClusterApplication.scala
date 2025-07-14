package com.cluster

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import com.cache.CacheActor
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ClusterApplication {

  private object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      val log = context.log
      log.info("Cluster node is starting up...")

      // Spawn optional logger actor (if you have one)
      context.spawn(ClusterLogger(), "ClusterLogger")

      // Initialize Cluster Sharding
      val sharding = ClusterSharding(context.system)
      log.info("Initializing Cluster Sharding for CacheActor")

      sharding.init(
        Entity(CacheActor.TypeKey) { entityContext =>
          log.info(s"Starting CacheActor entity: ${entityContext.entityId}")
          CacheActor()
        }
      )

      log.info("Cluster Sharding initialized successfully.")
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

    println(s"Starting ClusterApplication on port $port...")

    val system = ActorSystem[Nothing](RootBehavior(), "cluster-test", config)

    println(s"ClusterApplication started on port $port with system: ${system.name}")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
