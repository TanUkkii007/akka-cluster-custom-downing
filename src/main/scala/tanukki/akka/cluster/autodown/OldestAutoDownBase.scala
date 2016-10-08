package tanukki.akka.cluster.autodown

import akka.cluster.MemberStatus.Down
import akka.cluster.{MemberStatus, Member}

import scala.concurrent.duration.FiniteDuration

abstract class OldestAutoDownBase(oldestMemberRole: Option[String], downIfAlone: Boolean, autoDownUnreachableAfter: FiniteDuration)
  extends OldestAwareCustomAutoDownBase(autoDownUnreachableAfter){

  override def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {
    if (isOldestOf(oldestMemberRole))
      downPendingUnreachableMembers()
  }

  override def downOrAddPending(member: Member): Unit = {
    if (downIfAlone && isOldestAlone(oldestMemberRole)) {
      downAloneOldest(member)
    } else if (isOldestOf(oldestMemberRole)) {
      down(member.address)
      replaceMember(member.copy(Down))
    } else if (oldestMember(oldestMemberRole).contains(member)) {
      shutdownSelf()
    } else {
      pendingAsUnreachable(member)
    }
  }


  override def downOrAddPendingAll(members: Set[Member]): Unit = {
    val oldest = oldestMember(oldestMemberRole)
    if (downIfAlone) {
      if (isOldestAlone(oldestMemberRole)) {
        // ToDo: down-if-alone should be implemented here
      }
    } else {
      if (oldest.fold(true)(o => members.contains(o))) {
        shutdownSelf()
      } else {
        members.foreach(downOrAddPending)
      }
    }
  }

  def downAloneOldest(member: Member): Unit = {
    val oldest = oldestMember(oldestMemberRole)
    if (isOldestOf(oldestMemberRole)) {
      shutdownSelf()
    } else if (isSecondaryOldest(oldestMemberRole) && oldest.contains(member)) {
      oldest.foreach { m =>
        down(m.address)
        replaceMember(m.copy(Down))
      }
    } else {
      pendingAsUnreachable(member)
    }
  }
}
