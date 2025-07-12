package com.cache

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.repository.CacheRepository
import com.utils.CborSerializable

object CacheActor {
  sealed trait Command extends  CborSerializable
  case class Put(key: String, value: String, replyTo: ActorRef[Response]) extends Command
  case class Get(key: String, replyTo: ActorRef[Response]) extends Command
  case class Delete(key: String, replyTo: ActorRef[Response]) extends Command

  sealed trait Response extends CborSerializable
  case class ValueFound(value: String) extends Response
  case object ValueNotFound extends Response
  case object PutSuccess extends Response
  case object DeleteSuccess extends Response

  def apply(repo: CacheRepository): Behavior[Command] =
    Behaviors.setup { context =>

      Behaviors.receiveMessage {
        case Put(key, value, replyTo) =>
          repo.put(key, value)
          context.log.info(s"Stored [$key -> $value]")
          replyTo ! PutSuccess
          Behaviors.same

        case Get(key, replyTo) =>
          val result = repo.get(key)
          context.log.info(s"Get [$key] -> ${result.getOrElse("None")}")
          val response = result match {
            case Some(value) => ValueFound(value)
            case None => ValueNotFound
          }
          replyTo ! response
          Behaviors.same

        case Delete(key, replyTo) =>
          val deleted = repo.delete(key)
          context.log.info(s"Delete [$key] -> $deleted")
          replyTo ! DeleteSuccess
          Behaviors.same

        case v => context.log.info(s"Delete [$v")
          Behaviors.same
      }
    }
}
