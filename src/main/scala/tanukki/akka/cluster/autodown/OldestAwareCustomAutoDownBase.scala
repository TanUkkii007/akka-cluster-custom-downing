package tanukki.akka.cluster.autodown

import akka.cluster.ClusterEvent._
import akka.cluster.{MemberStatus, Member}
import scala.collection.immutable
import scala.collection.immutable.SortedSet
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
    targetMembers(role).headOption.map(_.address).contains(selfAddress)
  }

  def isOldest: Boolean = {
    isAllIntermediateMemberRemoved && isOldestUnsafe(None)
  }

  def isOldestOf(role: Option[String]): Boolean = {
    isAllIntermediateMemberRemoved && isOldestUnsafe(role)
  }

  def isOldestAlone(role: Option[String]): Boolean = {
    val tm = targetMembers(role)
    if (tm.isEmpty || tm.size == 1) true
    else {
      val oldest = tm.head
      val rest = tm.tail
      if (isOldestUnsafe(role)) {
        isOK(oldest) && rest.forall(isKO)
      } else {
        isKO(oldest) && rest.forall(isOK)
      }
    }
  }

  def isSecondaryOldest(role: Option[String]) = {
    val tm = targetMembers(role)
    if (tm.size >= 2) {
      tm.slice(1, 2).head.address == selfAddress
    }
    else false
  }

  def oldestMember(role: Option[String]): Option[Member] = targetMembers(role).headOption

  private def targetMembers(role: Option[String]): SortedSet[Member] = {
    role.fold(membersByAge)(r => membersByAge.filter(_.hasRole(r)))
  }

  private def isOK(member: Member) = {
    (member.status == MemberStatus.Up || member.status == MemberStatus.Leaving) &&
    (!pendingUnreachableMembers.contains(member) && !unstableUnreachableMembers.contains(member))
  }

  private def isKO(member: Member): Boolean = !isOK(member)
}