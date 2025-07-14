package com.cache

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.{LWWMap, LWWMapKey, SelfUniqueAddress}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator, ReplicatorMessageAdapter}
import com.utils.CborSerializable
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey

object CacheActor {

  val TypeKey: EntityTypeKey[CacheActor.Command] =
    EntityTypeKey[CacheActor.Command]("CacheActor")

  // Commands
  sealed trait Command extends CborSerializable

  final case class Put(key: String, value: String, replyTo: ActorRef[Response]) extends Command

  final case class Get(key: String, replyTo: ActorRef[Response]) extends Command

  final case class Delete(key: String, replyTo: ActorRef[Response]) extends Command

  // Responses
  sealed trait Response extends CborSerializable

  final case class ValueFound(value: String) extends Response

  case object ValueNotFound extends Response

  case object PutSuccess extends Response

  case object DeleteSuccess extends Response

  // Internal protocol
  private sealed trait InternalCommand extends Command

  private final case class InternalGetResponse(key: String, replyTo: ActorRef[Response], rsp: Replicator.GetResponse[LWWMap[String, String]]) extends InternalCommand

  private final case class InternalUpdateResponse(replyTo: ActorRef[Response], rsp: Replicator.UpdateResponse[LWWMap[String, String]], success: Response) extends InternalCommand

  private val DataKey: LWWMapKey[String, String] = LWWMapKey("cache")

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

    DistributedData.withReplicatorMessageAdapter[Command, LWWMap[String, String]] { replicatorAdapter =>
      Behaviors.receiveMessage {

        case Put(key, value, replyTo) =>
          context.log.info(s"[PUT] Request received for key: $key, value: $value")
          replicatorAdapter.askUpdate(
            askReplyTo =>
              Replicator.Update(DataKey, LWWMap.empty[String, String], Replicator.WriteLocal, askReplyTo)(
                _.put(node, key, value)
              ),
            rsp => InternalUpdateResponse(replyTo, rsp, PutSuccess)
          )
          Behaviors.same

        case Delete(key, replyTo) =>
          context.log.info(s"[DELETE] Request received for key: $key")
          replicatorAdapter.askUpdate(
            askReplyTo =>
              Replicator.Update(DataKey, LWWMap.empty[String, String], Replicator.WriteLocal, askReplyTo)(
                _.remove(node, key)
              ),
            rsp => InternalUpdateResponse(replyTo, rsp, DeleteSuccess)
          )

          Behaviors.same

        case Get(key, replyTo) =>
          context.log.info(s"[GET] Request received for key: $key")
          replicatorAdapter.askGet(
            askReplyTo => Replicator.Get(DataKey, Replicator.ReadLocal, askReplyTo),
            rsp => InternalGetResponse(key, replyTo, rsp)
          )
          Behaviors.same

        case InternalGetResponse(key, replyTo, rsp) =>
          rsp match {
            case g@Replicator.GetSuccess(`DataKey`) =>
              val valueOpt = g.get(DataKey).get(key)
              valueOpt match {
                case Some(value) =>
                  context.log.info(s"[GET] Found key: $key with value: $value")
                  replyTo ! ValueFound(value)
                case None =>
                  context.log.info(s"[GET] Key not found: $key")
                  replyTo ! ValueNotFound
              }
            case Replicator.NotFound(`DataKey`) =>
              context.log.info(s"[GET] DataKey not found for key: $key")
              replyTo ! ValueNotFound
            case _: Replicator.GetFailure[_] =>
              context.log.warn(s"[GET] Get failure for key: $key")
              replyTo ! ValueNotFound

            case unknown =>
              context.log.warn(s"Unhandled GetResponse: $unknown for key: $key")
              replyTo ! ValueNotFound
          }
          Behaviors.same

        case InternalUpdateResponse(replyTo, _: Replicator.UpdateSuccess[_], successReply) =>
          context.log.info(s"[UPDATE] Successful update, replying with: $successReply")
          replyTo ! successReply
          Behaviors.same

        case InternalUpdateResponse(replyTo, _: Replicator.UpdateFailure[_], _) =>
          context.log.error("[UPDATE] Update failed")
          replyTo ! ValueNotFound
          Behaviors.same

        case InternalUpdateResponse(replyTo, other, _) =>
          context.log.warn(s"Unhandled UpdateResponse: $other")
          replyTo ! ValueNotFound
          Behaviors.same
      }
    }
  }
}
