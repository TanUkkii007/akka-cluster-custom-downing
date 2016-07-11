package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{ActorSystem, Props, Address}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._

final class LeaderAutoDowningRoles(system: ActorSystem) extends DowningProvider {

  private def cluster = Cluster(system)

  override def downRemovalMargin: FiniteDuration = cluster.downingProvider.downRemovalMargin

  override def downingActorProps: Option[Props] = {
    val roles = system.settings.config.getStringList("custom-downing.leader-auto-downing-roles.target-roles").asScala.toSet
      cluster.settings.AutoDownUnreachableAfter match {
      case d: FiniteDuration => if (roles.isEmpty) None else Some(LeaderAutoDownRoles.props(roles, d))
      case _ =>
        throw new ConfigurationException("LeaderAutoDowningRoles downing provider selected but 'akka.cluster.auto-down-unreachable-after' not set")
    }
  }
}


private[autodown] object LeaderAutoDownRoles {
  def props(targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration): Props = Props(classOf[LeaderAutoDownRoles], targetRoles, autoDownUnreachableAfter)
}

private[autodown] class LeaderAutoDownRoles(targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration)
  extends LeaderAutoDownRolesBase(targetRoles, autoDownUnreachableAfter) with ClusterCustomDowning {

  override def down(node: Address): Unit = {
    log.info("Leader is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }
}
