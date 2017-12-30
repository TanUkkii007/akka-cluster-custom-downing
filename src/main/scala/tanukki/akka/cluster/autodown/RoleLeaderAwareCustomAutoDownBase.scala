package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.event.Logging

import scala.concurrent.duration.FiniteDuration

abstract class RoleLeaderAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration) extends CustomAutoDownBase(autoDownUnreachableAfter) {

  private val log = Logging(context.system, this)

  private var roleLeader: Map[String, Boolean] = Map.empty

  def isRoleLeaderOf(role: String): Boolean = roleLeader.getOrElse(role, false)

  def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {}

  override def receiveEvent: Receive = {
    case RoleLeaderChanged(role, leaderOption) =>
      roleLeader = roleLeader + (role -> leaderOption.contains(selfAddress))
      if (isRoleLeaderOf(role)) {
        log.info("This node is the new role leader for role {}", role)
      }
      onRoleLeaderChanged(role, leaderOption)
    case UnreachableMember(m) =>
      log.info("{} is unreachable", m)
      unreachableMember(m)
    case ReachableMember(m)   =>
      log.info("{} is reachable", m)
      remove(m)
    case MemberRemoved(m, _)  =>
      log.info("{} was removed from the cluster", m)
      remove(m)
  }

  override def initialize(state: CurrentClusterState): Unit = {
    roleLeader = state.roleLeaderMap.mapValues(_.exists(_ == selfAddress))
    super.initialize(state)
  }
}