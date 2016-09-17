package tanukki.akka.cluster.autodown

import akka.cluster.MemberStatus.Down
import akka.cluster.{MemberStatus, Member}

import scala.concurrent.duration.FiniteDuration

abstract class OldestAutoDownRolesBase(oldestMemberRole: Option[String], targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration)
  extends OldestAwareCustomAutoDownBase(autoDownUnreachableAfter){

  override def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {
    if (isOldestOf(oldestMemberRole))
      downPendingUnreachableMembers()
  }

  override def downOrAddPending(member: Member): Unit = {
    if (targetRoles.exists(role => member.hasRole(role))) {
      if (isOldestOf(oldestMemberRole)) {
        down(member.address)
        replaceMember(member.copy(Down))
      } else {
        pendingAsUnreachable(member)
      }
    }
  }
}
