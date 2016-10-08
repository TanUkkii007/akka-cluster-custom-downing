package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{Address, Props, ActorSystem}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.Await
import scala.concurrent.duration._

class OldestAutoDowning(system: ActorSystem) extends DowningProvider {
  private def clusterSettings = Cluster(system).settings

  override def downRemovalMargin: FiniteDuration = clusterSettings.DownRemovalMargin

  override def downingActorProps: Option[Props] = {
    val oldestMemberRole = {
      val r = system.settings.config.getString("custom-downing.oldest-auto-downing-roles.oldest-member-role")
      if (r.isEmpty) None else Some(r)
    }
    // Not supported yet.
    //val downIfAlone = system.settings.config.getBoolean("custom-downing.oldest-auto-downing-roles.down-if-alone")
    val downIfAlone = false
    val shutdownActorSystem = system.settings.config.getBoolean("custom-downing.oldest-auto-downing-roles.shutdown-actor-system-on-resolution")
    clusterSettings.AutoDownUnreachableAfter match {
      case d: FiniteDuration =>
        if (d == Duration.Zero && downIfAlone) throw new ConfigurationException("If you set down-if-alone=true, autodown timeout must be greater than zero.")
        Some(OldestAutoDown.props(oldestMemberRole, downIfAlone, shutdownActorSystem, d))
      case _ =>
        throw new ConfigurationException("OldestAutoDowningRoles downing provider selected but 'akka.cluster.auto-down-unreachable-after' not set")
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
