package tanukki.akka.cluster.autodown

import akka.actor.{ActorSystem, Address, Props}
import akka.cluster.{Cluster, DowningProvider}
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration.{FiniteDuration, _}

final class LeaderAutoDowningRoles(system: ActorSystem) extends DowningProvider {

  private[this] val cluster = Cluster(system)

  private val config: Config = system.settings.config

  override def downRemovalMargin: FiniteDuration = {
    val key = "custom-downing.down-removal-margin"
    config.getString(key) match {
      case "off" => Duration.Zero
      case _     => Duration(config.getDuration(key, MILLISECONDS), MILLISECONDS)
    }
  }

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
