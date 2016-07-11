package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{ActorSystem, Props, Address}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._

final class RoleLeaderAutoDowningRoles(system: ActorSystem) extends DowningProvider {

  private def cluster = Cluster(system)

  override def downRemovalMargin: FiniteDuration = cluster.downingProvider.downRemovalMargin

  override def downingActorProps: Option[Props] = {
    val leaderRole = system.settings.config.getString("custom-downing.role-leader-auto-downing-roles.leader-role")
    val roles = system.settings.config.getStringList("custom-downing.role-leader-auto-downing-roles.target-roles").asScala.toSet
    cluster.settings.AutoDownUnreachableAfter match {
      case d: FiniteDuration => if (roles.isEmpty) None else Some(RoleLeaderAutoDownRoles.props(leaderRole, roles, d))
      case _ =>
        throw new ConfigurationException("LeaderAutoDowningRoles downing provider selected but 'akka.cluster.auto-down-unreachable-after' not set")
    }
  }
}


private[autodown] object RoleLeaderAutoDownRoles {
  def props(leaderRole: String, targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration): Props = Props(classOf[RoleLeaderAutoDownRoles], leaderRole, targetRoles, autoDownUnreachableAfter)
}

private[autodown] class RoleLeaderAutoDownRoles(leaderRole: String, targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration)
  extends RoleLeaderAutoDownRolesBase(leaderRole, targetRoles, autoDownUnreachableAfter) with ClusterCustomDowning {

  override def down(node: Address): Unit = {
    log.info("RoleLeader is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }
}
