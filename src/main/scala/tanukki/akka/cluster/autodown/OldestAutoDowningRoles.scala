package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{Address, Props, ActorSystem}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._

class OldestAutoDowningRoles(system: ActorSystem) extends DowningProvider {
  private def clusterSettings = Cluster(system).settings

  override def downRemovalMargin: FiniteDuration = clusterSettings.DownRemovalMargin

  override def downingActorProps: Option[Props] = {
    val oldestMemberRole = {
      val r = system.settings.config.getString("custom-downing.oldest-auto-downing-roles.oldest-member-role")
      if (r.isEmpty) None else Some(r)
    }
    val roles = system.settings.config.getStringList("custom-downing.oldest-auto-downing-roles.target-roles").asScala.toSet
    clusterSettings.AutoDownUnreachableAfter match {
      case d: FiniteDuration => if (roles.isEmpty) None else Some(OldestAutoDownRoles.props(oldestMemberRole, roles, d))
      case _ =>
        throw new ConfigurationException("OldestAutoDowningRoles downing provider selected but 'akka.cluster.auto-down-unreachable-after' not set")
    }
  }
}

private[autodown] object OldestAutoDownRoles {
  def props(oldestMemberRole: Option[String], targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration): Props =
    Props(classOf[OldestAutoDownRoles], oldestMemberRole, targetRoles, autoDownUnreachableAfter)
}

private[autodown] class OldestAutoDownRoles(oldestMemberRole: Option[String], targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration)
  extends OldestAutoDownRolesBase(oldestMemberRole, targetRoles, autoDownUnreachableAfter) with ClusterCustomDowning {

  override def down(node: Address): Unit = {
    log.info("Oldest is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }
}
