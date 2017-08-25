package tanukki.akka.cluster.autodown

import akka.cluster.MemberStatus.Down
import akka.cluster.{MemberStatus, Member}

import scala.concurrent.duration.FiniteDuration

abstract class MajorityAutoDownBase(majorityMemberRole: Option[String], autoDownUnreachableAfter: FiniteDuration)
    extends MajorityAwareCustomAutoDownBase(autoDownUnreachableAfter) {


  override def downOrAddPending(member: Member): Unit = {
    if (isMajority(majorityMemberRole)) {
      down(member.address)
      replaceMember(member.copy(Down))
    } else {
      pendingAsUnreachable(member)
    }
  }

  override def downOrAddPendingAll(members: Set[Member]): Unit = {
    if (isMajority(majorityMemberRole)) {
      members.foreach({member =>
        down(member.address)
        replaceMember(member.copy(Down))
      })
    } else {
      shutdownSelf()
    }
  }
}
