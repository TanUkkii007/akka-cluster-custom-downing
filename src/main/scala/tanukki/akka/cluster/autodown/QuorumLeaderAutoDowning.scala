package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{Address, Props, ActorSystem}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.Await
import scala.concurrent.duration._

class QuorumLeaderAutoDowning(system: ActorSystem) extends DowningProvider {

  private def clusterSettings = Cluster(system).settings

  override def downRemovalMargin: FiniteDuration = clusterSettings.DownRemovalMargin

  override def downingActorProps: Option[Props] = {
    val stableAfter = system.settings.config.getDuration("custom-downing.stable-after").toMillis millis
    val role = {
      val r = system.settings.config.getString("custom-downing.quorum-leader-auto-downing.role")
      if (r.isEmpty) None else Some(r)
    }
    val quorumSize = system.settings.config.getInt("custom-downing.quorum-leader-auto-downing.quorum-size")
    val downIfOutOfQuorum = system.settings.config.getBoolean("custom-downing.quorum-leader-auto-downing.down-if-out-of-quorum")
    val shutdownActorSystem = system.settings.config.getBoolean("custom-downing.quorum-leader-auto-downing.shutdown-actor-system-on-resolution")
    Some(QuorumLeaderAutoDown.props(role, quorumSize, downIfOutOfQuorum, shutdownActorSystem, stableAfter))
  }
}


private[autodown] object QuorumLeaderAutoDown {
  def props(quorumRole: Option[String], quorumSize: Int, downIfOutOfQuorum: Boolean, shutdownActorSystem: Boolean, autoDownUnreachableAfter: FiniteDuration): Props =
    Props(classOf[QuorumLeaderAutoDown], quorumRole, quorumSize, downIfOutOfQuorum, shutdownActorSystem, autoDownUnreachableAfter)
}

private[autodown] class QuorumLeaderAutoDown(quorumRole: Option[String], quorumSize: Int, downIfOutOfQuorum: Boolean, shutdownActorSystem: Boolean, autoDownUnreachableAfter: FiniteDuration)
  extends QuorumLeaderAutoDownBase(quorumRole, quorumSize, downIfOutOfQuorum, autoDownUnreachableAfter) with ClusterCustomDowning {

  override def down(node: Address): Unit = {
    log.info("Quorum leader is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }

  override def shutdownSelf(): Unit = {
    if (shutdownActorSystem) {
      Await.result(context.system.terminate(), 10 seconds)
    } else {
      throw new SplitBrainResolvedError("QuorumLeaderAutoDowning")
    }
  }
}
