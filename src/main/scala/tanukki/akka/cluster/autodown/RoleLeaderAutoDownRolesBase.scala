package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.Member
import scala.concurrent.duration.FiniteDuration

abstract class RoleLeaderAutoDownRolesBase(leaderRole: String, targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration)
  extends RoleLeaderAwareCustomAutoDownBase(autoDownUnreachableAfter){


  override def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {
    if (leaderRole == role && isRoleLeaderOf(leaderRole)) downPendingUnreachableMembers()
  }

  override def downOrAddPending(member: Member): Unit = {
    if (targetRoles.exists(role => member.hasRole(role))) {
      if (isRoleLeaderOf(leaderRole)) {
        down(member.address)
      } else {
        pendingAsUnreachable(member)
      }
    }
  }
}
