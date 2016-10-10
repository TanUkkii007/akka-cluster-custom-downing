package tanukki.akka.cluster.autodown

import akka.actor.{ActorSystem, Props, Address}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._
import scala.concurrent.duration._

final class LeaderAutoDowningRoles(system: ActorSystem) extends DowningProvider {

  private def clusterSettings = Cluster(system).settings

  override def downRemovalMargin: FiniteDuration = clusterSettings.DownRemovalMargin

  override def downingActorProps: Option[Props] = {
    val stableAfter = system.settings.config.getDuration("custom-downing.stable-after").toMillis millis
    val roles = system.settings.config.getStringList("custom-downing.leader-auto-downing-roles.target-roles").asScala.toSet
    if (roles.isEmpty) None else Some(LeaderAutoDownRoles.props(roles, stableAfter))
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
