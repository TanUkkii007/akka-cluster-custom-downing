package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{ActorSystem, Address, Props}
import akka.cluster.{Cluster, DowningProvider}
import com.typesafe.config.Config

import scala.concurrent.Await
import scala.concurrent.duration._

class OldestAutoDowning(system: ActorSystem) extends DowningProvider {

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
    val oldestMemberRole = {
      val r = system.settings.config.getString("custom-downing.oldest-auto-downing.oldest-member-role")
      if (r.isEmpty) None else Some(r)
    }
    val downIfAlone = system.settings.config.getBoolean("custom-downing.oldest-auto-downing.down-if-alone")
    val shutdownActorSystem = system.settings.config.getBoolean("custom-downing.oldest-auto-downing.shutdown-actor-system-on-resolution")
    if (stableAfter == Duration.Zero && downIfAlone) throw new ConfigurationException("If you set down-if-alone=true, stable-after timeout must be greater than zero.")
    else {
      Some(OldestAutoDown.props(oldestMemberRole, downIfAlone, shutdownActorSystem, stableAfter))
    }
  }
}

private[autodown] object OldestAutoDown {
  def props(oldestMemberRole: Option[String], downIfAlone: Boolean, shutdownActorSystem: Boolean, autoDownUnreachableAfter: FiniteDuration): Props =
    Props(classOf[OldestAutoDown], oldestMemberRole, downIfAlone, shutdownActorSystem, autoDownUnreachableAfter)
}

private[autodown] class OldestAutoDown(oldestMemberRole: Option[String], downIfAlone: Boolean, shutdownActorSystem: Boolean, autoDownUnreachableAfter: FiniteDuration)
  extends OldestAutoDownBase(oldestMemberRole, downIfAlone, autoDownUnreachableAfter) with ClusterCustomDowning {

  override def down(node: Address): Unit = {
    log.info("Oldest is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }

  override def shutdownSelf(): Unit = {
    if (shutdownActorSystem) {
      Await.result(context.system.terminate(), 10 seconds)
    } else {
      throw new SplitBrainResolvedError("OldestAutoDowning")
    }
  }
}
