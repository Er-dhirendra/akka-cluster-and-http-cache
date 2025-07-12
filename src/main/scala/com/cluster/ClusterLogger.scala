package com.cluster

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent._
import akka.cluster.typed.{Cluster, Subscribe, Unsubscribe}

object ClusterLogger {

  sealed trait Event

  // Internal adapted cluster events
  private final case class ReachabilityChange(event: ReachabilityEvent) extends Event
  private final case class MemberChange(event: MemberEvent) extends Event

  def apply(): Behavior[Event] = Behaviors.setup { ctx =>
    val cluster = Cluster(ctx.system)

    val memberEventAdapter: ActorRef[MemberEvent] =
      ctx.messageAdapter(MemberChange.apply)
    cluster.subscriptions ! Subscribe(memberEventAdapter, classOf[MemberEvent])

    val reachabilityAdapter: ActorRef[ReachabilityEvent] =
      ctx.messageAdapter(ReachabilityChange.apply)
    cluster.subscriptions ! Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

    Behaviors
      .receiveMessage[Event] {
        case ReachabilityChange(UnreachableMember(member)) =>
          ctx.log.info("Member detected as unreachable: {}", member)
          Behaviors.same

        case ReachabilityChange(ReachableMember(member)) =>
          ctx.log.info("Member back to reachable: {}", member)
          Behaviors.same

        case MemberChange(MemberUp(member)) =>
          ctx.log.info("Member is Up: {}", member.address)
          Behaviors.same

        case MemberChange(MemberRemoved(member, previousStatus)) =>
          ctx.log.info("Member is Removed: {} after {}", member.address, previousStatus)
          Behaviors.same

        case MemberChange(_: MemberEvent) =>
          // Other member events can be ignored
          Behaviors.same
      }
      .receiveSignal {
        case (ctx, akka.actor.typed.PostStop) =>
          cluster.subscriptions ! Unsubscribe(memberEventAdapter)
          cluster.subscriptions ! Unsubscribe(reachabilityAdapter)
          ctx.log.info("Unsubscribed from cluster events on stop.")
          Behaviors.same
      }
  }
}
