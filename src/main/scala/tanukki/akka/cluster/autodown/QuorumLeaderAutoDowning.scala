package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{Address, Props, ActorSystem}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.duration.FiniteDuration

class QuorumLeaderAutoDowning(system: ActorSystem) extends DowningProvider {

  private def clusterSettings = Cluster(system).settings

  override def downRemovalMargin: FiniteDuration = clusterSettings.DownRemovalMargin

  override def downingActorProps: Option[Props] = {
    val role = {
      val r = system.settings.config.getString("custom-downing.quorum-leader-auto-downing.role")
      if (r.isEmpty) None else Some(r)
    }
    val quorumSize = system.settings.config.getInt("custom-downing.quorum-leader-auto-downing.quorum-size")
    val downIfOutOfQuorum = system.settings.config.getBoolean("custom-downing.quorum-leader-auto-downing.down-if-out-of-quorum")
    clusterSettings.AutoDownUnreachableAfter match {
      case d: FiniteDuration => Some(QuorumLeaderAutoDown.props(role, quorumSize, downIfOutOfQuorum, d))
      case _ =>
        throw new ConfigurationException("QuorumLeaderAutoDowning downing provider selected but 'akka.cluster.auto-down-unreachable-after' not set")
    }
  }
}


private[autodown] object QuorumLeaderAutoDown {
  def props(quorumRole: Option[String], quorumSize: Int, downIfOutOfQuorum: Boolean, autoDownUnreachableAfter: FiniteDuration): Props =
    Props(classOf[QuorumLeaderAutoDown], quorumRole, quorumSize, downIfOutOfQuorum, autoDownUnreachableAfter)
}

private[autodown] class QuorumLeaderAutoDown(quorumRole: Option[String], quorumSize: Int, downIfOutOfQuorum: Boolean, autoDownUnreachableAfter: FiniteDuration)
  extends QuorumLeaderAutoDownBase(quorumRole, quorumSize, downIfOutOfQuorum, autoDownUnreachableAfter) with ClusterCustomDowning {

  override def down(node: Address): Unit = {
    log.info("Quorum leader is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }
}
