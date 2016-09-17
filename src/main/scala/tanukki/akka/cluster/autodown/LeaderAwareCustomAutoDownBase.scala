package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.ClusterEvent._
import scala.concurrent.duration.FiniteDuration

abstract class LeaderAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration) extends CustomAutoDownBase(autoDownUnreachableAfter) {

  private var leader = false

  def onLeaderChanged(leader: Option[Address]): Unit = {}

  def isLeader: Boolean = leader

  override def receiveEvent: Receive = {
    case LeaderChanged(leaderOption) =>
      leader = leaderOption.exists(_ == selfAddress)
      onLeaderChanged(leaderOption)

    case UnreachableMember(m) => unreachableMember(m)
    case ReachableMember(m)   => remove(m)
    case MemberRemoved(m, _)  => remove(m)
  }

  override def initialize(state: CurrentClusterState): Unit = {
    leader = state.leader.exists(_ == selfAddress)
    super.initialize(state)
  }
}
