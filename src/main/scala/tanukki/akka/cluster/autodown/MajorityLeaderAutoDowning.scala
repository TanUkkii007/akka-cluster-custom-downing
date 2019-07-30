package tanukki.akka.cluster.autodown

import akka.actor.{ActorSystem, Address, Props}
import akka.cluster.{Cluster, DowningProvider}
import com.typesafe.config.Config

import scala.concurrent.Await
import scala.concurrent.duration._

class MajorityLeaderAutoDowning(system: ActorSystem) extends DowningProvider {

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
    val stableAfter = config.getDuration("custom-downing.stable-after").toMillis millis
    val majorityMemberRole = {
      val r = config.getString("custom-downing.majority-leader-auto-downing.majority-member-role")
      if (r.isEmpty) None else Some(r)
    }
    val downIfInMinority = config.getBoolean("custom-downing.majority-leader-auto-downing.down-if-in-minority")
    val shutdownActorSystem = config.getBoolean("custom-downing.majority-leader-auto-downing.shutdown-actor-system-on-resolution")
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
