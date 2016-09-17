package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.Member

import scala.concurrent.duration.FiniteDuration


abstract class LeaderAutoDownRolesBase(targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration)
  extends LeaderAwareCustomAutoDownBase(autoDownUnreachableAfter){


  override def onLeaderChanged(leader: Option[Address]): Unit = {
    if (isLeader) downPendingUnreachableMembers()
  }

  override def downOrAddPending(member: Member): Unit = {
    if (targetRoles.exists(role => member.hasRole(role))) {
      if (isLeader) {
        down(member.address)
      } else {
        pendingAsUnreachable(member)
      }
    }
  }
}
