package tanukki.akka.cluster.autodown

import akka.cluster.ClusterEvent._
import akka.cluster.{MemberStatus, Member}
import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.FiniteDuration

abstract class MajorityAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration)
    extends CustomAutoDownBase(autoDownUnreachableAfter) with SplitBrainResolver {

  private var membersByAddress: immutable.SortedSet[Member] = immutable.SortedSet.empty(Member.ordering)

  def receiveEvent = {
    case MemberUp(m) =>
      replaceMember(m)
    case UnreachableMember(m) =>
      replaceMember(m)
      unreachableMember(m)

    case ReachableMember(m) =>
      replaceMember(m)
      remove(m)
    case MemberLeft(m) =>
      replaceMember(m)
    case MemberExited(m) =>
      replaceMember(m)
    case MemberRemoved(m, prev) =>
      remove(m)
      removeMember(m)
      //onMemberRemoved(m, prev)
  }

  //def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {}

  override def initialize(state: CurrentClusterState): Unit = {
    membersByAddress = immutable.SortedSet.empty(Member.ordering) union state.members.filterNot {m =>
      m.status == MemberStatus.Removed
    }
    super.initialize(state)
  }

  def replaceMember(member: Member): Unit = {
    membersByAddress -= member
    membersByAddress += member
  }

  def removeMember(member: Member): Unit = {
    membersByAddress -= member
  }

  def isMajority(role: Option[String]): Boolean = {
    val targets = targetMembers(role)
    val okMembers = targets filter isOK
    val koMembers = targets -- okMembers

    val isEqual = okMembers.size == koMembers.size
    return (okMembers.size > koMembers.size ||
      isEqual && okMembers.headOption.map(okMembers.contains(_)).getOrElse(true))
  }

  private def targetMembers(role: Option[String]): SortedSet[Member] = {
    role.fold(membersByAddress)(r => membersByAddress.filter(_.hasRole(r)))
  }

  private def isOK(member: Member) = {
    (member.status == MemberStatus.Up || member.status == MemberStatus.Leaving) &&
    (!pendingUnreachableMembers.contains(member) && !unstableUnreachableMembers.contains(member))
  }

  private def isKO(member: Member): Boolean = !isOK(member)
}
