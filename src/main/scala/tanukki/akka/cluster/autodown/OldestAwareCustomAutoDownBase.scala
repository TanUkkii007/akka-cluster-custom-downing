package tanukki.akka.cluster.autodown

import akka.cluster.ClusterEvent._
import akka.cluster.{MemberStatus, Member}
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

abstract class OldestAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration)
  extends CustomAutoDownBase(autoDownUnreachableAfter) {

  private var membersByAge: immutable.SortedSet[Member] = immutable.SortedSet.empty(Member.ageOrdering)

  def receiveEvent = {
    case MemberUp(m) =>
      replaceMember(m)
    case UnreachableMember(m) =>
      replaceMember(m)
      unreachableMember(m)

    case ReachableMember(m)   =>
      replaceMember(m)
      remove(m)
    case MemberLeft(m) =>
      replaceMember(m)
    case MemberExited(m) =>
      replaceMember(m)
    case MemberRemoved(m, prev)  =>
      remove(m)
      removeMember(m)
      onMemberRemoved(m, prev)
  }

  def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {}

  override def initialize(state: CurrentClusterState): Unit = {
    membersByAge = immutable.SortedSet.empty(Member.ageOrdering) union state.members.filterNot {m =>
      m.status == MemberStatus.Removed
    }
    super.initialize(state)
  }

  def replaceMember(member: Member): Unit = {
    membersByAge -= member
    membersByAge += member
  }

  def removeMember(member: Member): Unit = {
    membersByAge -= member
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