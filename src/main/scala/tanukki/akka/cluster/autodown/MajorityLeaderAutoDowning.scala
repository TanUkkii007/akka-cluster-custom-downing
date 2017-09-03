package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{Address, Props, ActorSystem}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.Await
import scala.concurrent.duration._

class MajorityLeaderAutoDowning(system: ActorSystem) extends DowningProvider {
  private def clusterSettings = Cluster(system).settings

  override def downRemovalMargin: FiniteDuration = clusterSettings.DownRemovalMargin

  override def downingActorProps: Option[Props] = {
    val stableAfter = system.settings.config.getDuration("custom-downing.stable-after").toMillis millis
    val majorityMemberRole = {
      val r = system.settings.config.getString("custom-downing.majority-auto-downing.majority-member-role")
      if (r.isEmpty) None else Some(r)
    }
    val downIfInMinority = system.settings.config.getBoolean("custom-downing.majority-leader-auto-downing.down-if-in-minority")
    val shutdownActorSystem = system.settings.config.getBoolean("custom-downing.majority-leader-auto-downing.shutdown-actor-system-on-resolution")
    Some(MajorityLeaderAutoDown.props(majorityMemberRole, downIfInMinority, shutdownActorSystem, stableAfter))
  }
}

private[autodown] object MajorityLeaderAutoDown {
  def props(majorityMemberRole: Option[String], downIfInMinority: Boolean, shutdownActorSystem: Boolean, autoDownUnreachableAfter: FiniteDuration): Props =
    Props(classOf[MajorityLeaderAutoDown], majorityMemberRole, downIfInMinority, shutdownActorSystem, autoDownUnreachableAfter)
}

private[autodown] class MajorityLeaderAutoDown(majorityMemberRole: Option[String], downIfInMinority: Boolean, shutdownActorSystem: Boolean, autoDownUnreachableAfter: FiniteDuration)
  extends MajorityLeaderAutoDownBase(majorityMemberRole, downIfInMinority, autoDownUnreachableAfter) with ClusterCustomDowning {

  override def down(node: Address): Unit = {
    log.info("Majority is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }

  override def shutdownSelf(): Unit = {
    if (shutdownActorSystem) {
      Await.result(context.system.terminate(), 10 seconds)
    } else {
      throw new SplitBrainResolvedError("MajorityAutoDowning")
    }
  }
}
