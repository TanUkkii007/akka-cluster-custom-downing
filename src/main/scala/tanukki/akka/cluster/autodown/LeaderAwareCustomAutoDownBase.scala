package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.event.Logging

import scala.concurrent.duration.FiniteDuration

abstract class LeaderAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration) extends CustomAutoDownBase(autoDownUnreachableAfter) {

  private val log = Logging(context.system, this)

  private var leader = false

  def onLeaderChanged(leader: Option[Address]): Unit = {}

  def isLeader: Boolean = leader

  override def receiveEvent: Receive = {
    case LeaderChanged(leaderOption) =>
      leader = leaderOption.contains(selfAddress)
      if (isLeader) {
        log.info("This node is the new Leader")
      }
      onLeaderChanged(leaderOption)
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
    leader = state.leader.exists(_ == selfAddress)
    super.initialize(state)
  }
}
