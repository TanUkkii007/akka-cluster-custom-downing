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

  def scheduler: Scheduler

  def onLeaderChanged(leader: Option[Address]): Unit = {}

  def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {}

  import context.dispatcher

  val skipMemberStatus = Set[MemberStatus](Down, Exiting)

  private var scheduledUnreachable: Map[Member, Cancellable] = Map.empty
  private var pendingUnreachable: Set[Member] = Set.empty

  private var leader = false
  private var roleLeader: Map[String, Boolean] = Map.empty

  override def postStop(): Unit = {
    scheduledUnreachable.values foreach { _.cancel }
  }

  def receive = {
    case state: CurrentClusterState =>
      leader = state.leader.exists(_ == selfAddress)
      state.unreachable foreach unreachableMember

    case UnreachableMember(m) => unreachableMember(m)

    case ReachableMember(m)   => remove(m)
    case MemberRemoved(m, _)  => remove(m)

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

  def isLeader: Boolean = leader

  def isRoleLeaderOf(role: String): Boolean = roleLeader.getOrElse(role, false)

  def scheduledUnreachableMembers: Map[Member, Cancellable] = scheduledUnreachable

  def pendingUnreachableMembers: Set[Member] = pendingUnreachable

  def pendingAsUnreachable(member: Member): Unit = pendingUnreachable += member

  def downPendingUnreachableMembers(): Unit = {
    pendingUnreachable.foreach(member => down(member.address))
    pendingUnreachable = Set.empty
  }

}