/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  *
  * 2016- Modified by Yusuke Yasuda
  * The original source code can be found here.
  * https://github.com/akka/akka/blob/master/akka-cluster/src/main/scala/akka/cluster/AutoDown.scala
  */

package tanukki.akka.cluster.autodown

import akka.actor.{Cancellable, Scheduler, Address, Actor}
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{Exiting, Down}
import akka.cluster._
import scala.concurrent.duration.{Duration, FiniteDuration}

object CustomDowning {
  case class UnreachableTimeout(member: Member)
}

abstract class CustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration) extends Actor {

  import CustomDowning._

  def selfAddress: Address

  def down(node: Address): Unit

  def downOrAddPending(member: Member): Unit

  def downOrAddPendingAll(members: Set[Member])

  def scheduler: Scheduler

  import context.dispatcher

  val skipMemberStatus = Set[MemberStatus](Down, Exiting)

  private var scheduledUnreachable: Map[Member, Cancellable] = Map.empty
  private var pendingUnreachable: Set[Member] = Set.empty
  private var unstableUnreachable: Set[Member] = Set.empty

  override def postStop(): Unit = {
    scheduledUnreachable.values foreach { _.cancel }
    super.postStop()
  }

  def receiveEvent: Receive

  def receive: Receive = receiveEvent orElse predefinedReceiveEvent

  def predefinedReceiveEvent: Receive = {
    case state: CurrentClusterState =>
      initialize(state)
      state.unreachable foreach unreachableMember

    case UnreachableTimeout(member) =>
      if (scheduledUnreachable contains member) {
        scheduledUnreachable -= member
        if (scheduledUnreachable.isEmpty) {
          unstableUnreachable += member
          downOrAddPendingAll(unstableUnreachable)
          unstableUnreachable = Set.empty
        } else {
          unstableUnreachable += member
        }
      }

    case _: ClusterDomainEvent =>
  }

  def initialize(state: CurrentClusterState) = {}

  def unreachableMember(m: Member): Unit =
    if (!skipMemberStatus(m.status) && !scheduledUnreachable.contains(m))
      scheduleUnreachable(m)

  def scheduleUnreachable(m: Member): Unit = {
    if (autoDownUnreachableAfter == Duration.Zero) {
      downOrAddPending(m)
    } else {
      val task = scheduler.scheduleOnce(autoDownUnreachableAfter, self, UnreachableTimeout(m))
      scheduledUnreachable += (m -> task)
    }
  }

  def remove(member: Member): Unit = {
    scheduledUnreachable.get(member) foreach { _.cancel }
    scheduledUnreachable -= member
    pendingUnreachable -= member
    unstableUnreachable -= member
  }

  def scheduledUnreachableMembers: Map[Member, Cancellable] = scheduledUnreachable

  def pendingUnreachableMembers: Set[Member] = pendingUnreachable

  def pendingAsUnreachable(member: Member): Unit = pendingUnreachable += member

  def downPendingUnreachableMembers(): Unit = {
    pendingUnreachable.foreach(member => down(member.address))
    pendingUnreachable = Set.empty
  }

  def unstableUnreachableMembers: Set[Member] = unstableUnreachable
}