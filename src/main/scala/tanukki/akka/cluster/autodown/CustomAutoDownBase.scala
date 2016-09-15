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

import scala.collection.immutable
import scala.concurrent.duration.{Duration, FiniteDuration}

object CustomDowning {
  case class UnreachableTimeout(member: Member)
}

abstract class CustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration) extends Actor {

  import CustomDowning._

  def selfAddress: Address

  def down(node: Address): Unit

  def downOrAddPending(member: Member): Unit

  def scheduler: Scheduler

  def onLeaderChanged(leader: Option[Address]): Unit = {}

  def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {}

  def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {}

  import context.dispatcher

  val skipMemberStatus = Set[MemberStatus](Down, Exiting)

  private var scheduledUnreachable: Map[Member, Cancellable] = Map.empty
  private var pendingUnreachable: Set[Member] = Set.empty

  private var leader = false
  private var roleLeader: Map[String, Boolean] = Map.empty
  private var membersByAge: immutable.SortedSet[Member] = immutable.SortedSet.empty(Member.ageOrdering)

  override def postStop(): Unit = {
    scheduledUnreachable.values foreach { _.cancel }
    super.postStop()
  }

  def receive = {
    case state: CurrentClusterState =>
      leader = state.leader.exists(_ == selfAddress)
      roleLeader = state.roleLeaderMap.mapValues(_.exists(_ == selfAddress))
      membersByAge = immutable.SortedSet.empty(Member.ageOrdering) union state.members.filterNot {m =>
        m.status == MemberStatus.Removed
      }
      state.unreachable foreach unreachableMember

    case MemberUp(m) =>
      replaceMember(m)
    case UnreachableMember(m) => unreachableMember(m)

    case ReachableMember(m)   => remove(m)
    case MemberLeft(m) =>
      replaceMember(m)
    case MemberExited(m) =>
      replaceMember(m)
    case MemberRemoved(m, prev)  =>
      remove(m)
      removeMember(m)
      onMemberRemoved(m, prev)

    case LeaderChanged(leaderOption) =>
      leader = leaderOption.exists(_ == selfAddress)
      onLeaderChanged(leaderOption)

    case RoleLeaderChanged(role, leaderOption) =>
      roleLeader = roleLeader + (role -> leaderOption.exists(_ == selfAddress))
      onRoleLeaderChanged(role, leaderOption)
    case UnreachableTimeout(member) =>
      if (scheduledUnreachable contains member) {
        scheduledUnreachable -= member
        downOrAddPending(member)
      }

    case _: ClusterDomainEvent => // not interested in other events

  }

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
  }

  def replaceMember(member: Member): Unit = {
    membersByAge -= member
    membersByAge += member
  }

  def removeMember(member: Member): Unit = {
    membersByAge -= member
  }

  def isLeader: Boolean = leader

  def isRoleLeaderOf(role: String): Boolean = roleLeader.getOrElse(role, false)

  def scheduledUnreachableMembers: Map[Member, Cancellable] = scheduledUnreachable

  def pendingUnreachableMembers: Set[Member] = pendingUnreachable

  def pendingAsUnreachable(member: Member): Unit = pendingUnreachable += member

  def downPendingUnreachableMembers(): Unit = {
    pendingUnreachable.foreach(member => down(member.address))
    pendingUnreachable = Set.empty
  }

  def isAllIntermediateMemberRemoved = {
    val isUnsafe = membersByAge.exists { m =>
      m.status == MemberStatus.Down || m.status == MemberStatus.Exiting
    }
    !isUnsafe
  }

  def isOldestUnsafe(role: Option[String]): Boolean = {
    val targetMember = role.fold(membersByAge)(r => membersByAge.filter(_.hasRole(r)))
    targetMember.headOption.map(_.address).contains(selfAddress)
  }

  def isOldest: Boolean = {
    isAllIntermediateMemberRemoved && isOldestUnsafe(None)
  }

  def isOldestOf(role: Option[String]): Boolean = {
    isAllIntermediateMemberRemoved && isOldestUnsafe(role)
  }
}