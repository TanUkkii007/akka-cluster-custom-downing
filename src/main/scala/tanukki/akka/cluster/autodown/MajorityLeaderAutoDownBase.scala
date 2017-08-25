package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.MemberStatus.Down
import akka.cluster.{MemberStatus, Member}

import scala.concurrent.duration.FiniteDuration

abstract class MajorityLeaderAutoDownBase(majorityMemberRole: Option[String], autoDownUnreachableAfter: FiniteDuration)
    extends MajorityAwareCustomAutoDownBase(autoDownUnreachableAfter) {

  override def onLeaderChanged(leader: Option[Address]): Unit = {
    if (majorityMemberRole.isEmpty && isLeader) downPendingUnreachableMembers()
  }

  override def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {
    majorityMemberRole.foreach { r =>
      if (r == role && isRoleLeaderOf(r)) downPendingUnreachableMembers()
    }
  }

  override def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {
    if (isMajority(majorityMemberRole)) {
      if (isLeaderOf(majorityMemberRole)) {
        downPendingUnreachableMembers()
      }
    } else {
      down(selfAddress)
    }
    super.onMemberRemoved(member, previousStatus)
  }

  override def downOrAddPending(member: Member): Unit = {
    if (isLeaderOf(majorityMemberRole)) {
      down(member.address)
      replaceMember(member.copy(Down))
    } else {
      pendingAsUnreachable(member)
    }
  }

  override def downOrAddPendingAll(members: Set[Member]): Unit = {
    if (isMajorityAfterDown(members, majorityMemberRole)) {
      members.foreach(downOrAddPending)
    } else {
      shutdownSelf()
    }
  }
}
