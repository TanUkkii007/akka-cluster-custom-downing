package tanukki.akka.cluster.autodown

import akka.cluster.MultiNodeClusterSpec
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory


final case class MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet: Boolean) extends MultiNodeConfig {
  val node_A_1 = role("node-A-1")
  val node_A_2 = role("node-A-2")
  val node_A_3 = role("node-A-3")
  val node_B_1 = role("node-B-1")
  val node_B_2 = role("node-B-2")

  commonConfig(ConfigFactory.parseString(
    """
      |akka.cluster.downing-provider-class = "tanukki.akka.cluster.autodown.RoleLeaderAutoDowningRoles"
      |custom-downing {
      |  stable-after = 0s
      |  role-leader-auto-downing-roles {
      |    leader-role = "role-A"
      |    target-roles = [role-B]
      |  }
      |}
      |akka.cluster.metrics.enabled=off
      |akka.actor.warn-about-java-serializer-usage = off
      |akka.remote.log-remote-lifecycle-events = off
    """.stripMargin)
    .withFallback(MultiNodeClusterSpec.clusterConfig(failureDetectorPuppet))
  )

  nodeConfig(node_A_1, node_A_2, node_A_3)(ConfigFactory.parseString(
    """
      |akka.cluster {
      |  roles = [role-A]
      |}
    """.stripMargin))

  nodeConfig(node_B_1, node_B_2)(ConfigFactory.parseString(
    """
      |akka.cluster {
      |  roles = [role-B]
      |}
    """.stripMargin))

}