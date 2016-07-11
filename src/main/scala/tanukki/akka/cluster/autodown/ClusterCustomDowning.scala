package tanukki.akka.cluster.autodown

import akka.actor.{Address, ActorLogging, Scheduler}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.ClusterDomainEvent
import scala.concurrent.duration._

trait ClusterCustomDowning extends ActorLogging { base: CustomAutoDownBase =>

  val cluster = Cluster(context.system)

  override def selfAddress: Address = cluster.selfAddress

  override def scheduler: Scheduler = {
    if (context.system.scheduler.maxFrequency < 1.second / cluster.settings.SchedulerTickDuration) {
      log.warning("CustomDowning does not use a cluster dedicated scheduler. Cluster will use a dedicated scheduler if configured " +
        "with 'akka.scheduler.tick-duration' [{} ms] >  'akka.cluster.scheduler.tick-duration' [{} ms].",
        (1000 / context.system.scheduler.maxFrequency).toInt, cluster.settings.SchedulerTickDuration.toMillis)
    }
    context.system.scheduler
  }

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[ClusterDomainEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }
}
