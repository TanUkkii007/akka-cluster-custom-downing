package tanukki.akka.cluster.autodown

import akka.cluster.MultiNodeClusterSpec
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory


final case class MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet: Boolean) extends MultiNodeConfig {
  val nodeA = role("nodeA")
  val nodeB = role("nodeB")
  val nodeC = role("nodeC")
  val nodeD = role("nodeD")
  val nodeE = role("nodeE")

  commonConfig(ConfigFactory.parseString(
    """
      |akka.cluster.downing-provider-class = "tanukki.akka.cluster.autodown.QuorumLeaderAutoDowning"
      |custom-downing {
      |  quorum-leader-auto-downing {
      |    role = "role"
      |    quorum-size = 3
      |    down-if-out-of-quorum = true
      |    shutdown-actor-system-on-resolution = false
      |  }
      |}
      |akka.cluster.auto-down-unreachable-after = 1s
      |akka.cluster.metrics.enabled=off
      |akka.actor.warn-about-java-serializer-usage = off
      |akka.remote.log-remote-lifecycle-events = off
    """.stripMargin)
    .withFallback(MultiNodeClusterSpec.clusterConfig(failureDetectorPuppet))
  )

  nodeConfig(nodeA, nodeB, nodeC, nodeD, nodeE)(ConfigFactory.parseString(
    """
      |akka.cluster {
      |  roles = [role]
      |}
    """.stripMargin))

}