package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.ClusterEvent._

import scala.concurrent.duration.FiniteDuration

abstract class RoleLeaderAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration) extends CustomAutoDownBase(autoDownUnreachableAfter) {

  private var roleLeader: Map[String, Boolean] = Map.empty

  def isRoleLeaderOf(role: String): Boolean = roleLeader.getOrElse(role, false)

  def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {}

  override def receiveEvent: Receive = {
    case RoleLeaderChanged(role, leaderOption) =>
      roleLeader = roleLeader + (role -> leaderOption.exists(_ == selfAddress))
      onRoleLeaderChanged(role, leaderOption)

    case UnreachableMember(m) => unreachableMember(m)
    case ReachableMember(m)   => remove(m)
    case MemberRemoved(m, _)  => remove(m)
  }

  override def initialize(state: CurrentClusterState): Unit = {
    roleLeader = state.roleLeaderMap.mapValues(_.exists(_ == selfAddress))
    super.initialize(state)
  }
}