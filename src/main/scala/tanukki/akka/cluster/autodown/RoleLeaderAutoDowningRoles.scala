package tanukki.akka.cluster.autodown

import akka.actor.{ActorSystem, Address, Props}
import akka.cluster.{Cluster, DowningProvider}
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration.{FiniteDuration, _}

final class RoleLeaderAutoDowningRoles(system: ActorSystem) extends DowningProvider {

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
    val leaderRole = system.settings.config.getString("custom-downing.role-leader-auto-downing-roles.leader-role")
    val roles = system.settings.config.getStringList("custom-downing.role-leader-auto-downing-roles.target-roles").asScala.toSet
    if (roles.isEmpty) None else Some(RoleLeaderAutoDownRoles.props(leaderRole, roles, stableAfter))
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
