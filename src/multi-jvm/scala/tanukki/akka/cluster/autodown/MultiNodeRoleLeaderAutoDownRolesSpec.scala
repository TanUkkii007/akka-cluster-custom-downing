/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  * 2016- modified by Yusuke Yasuda
  * original source code is at https://github.com/akka/akka/blob/master/akka-cluster/src/multi-jvm/scala/akka/cluster/LeaderDowningNodeThatIsUnreachableSpec.scala
  */

package tanukki.akka.cluster.autodown

import akka.cluster.MultiNodeClusterSpec
import akka.remote.testkit.{STMultiNodeSpec, MultiNodeSpec}
import akka.testkit.LongRunningTest
import scala.concurrent.duration._


class RoleLeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode1 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = true))
class RoleLeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode2 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = true))
class RoleLeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode3 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = true))
class RoleLeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode4 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = true))
class RoleLeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode5 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = true))

class RoleLeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode1 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = false))
class RoleLeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode2 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = false))
class RoleLeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode3 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = false))
class RoleLeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode4 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = false))
class RoleLeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode5 extends MultiNodeRoleLeaderAutoDownRolesSpec(MultiNodeRoleLeaderDownRolesSpecConfig(failureDetectorPuppet = false))

abstract class MultiNodeRoleLeaderAutoDownRolesSpec(multiNodeConfig: MultiNodeRoleLeaderDownRolesSpecConfig) extends MultiNodeSpec(multiNodeConfig)
with STMultiNodeSpec with MultiNodeClusterSpec {
  import multiNodeConfig._

  muteMarkingAsUnreachable()

  "The RoleLeader in a 4 node cluster" must {

    "be able to DOWN a 'last' node that is UNREACHABLE" taggedAs LongRunningTest in {
      awaitClusterUp(node_A_1, node_A_2, node_A_3, node_B_1, node_B_2)

      val fourthAddress = address(node_B_2)

      enterBarrier("before-exit-fourth-node")
      runOn(node_A_1) {
        // kill 'fourth' node
        testConductor.exit(node_B_2, 0).await
        enterBarrier("down-fourth-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(fourthAddress)

        // --- HERE THE LEADER SHOULD DETECT FAILURE AND AUTO-DOWN THE UNREACHABLE NODE ---

        awaitMembersUp(numberOfMembers = 4, canNotBePartOfMemberRing = Set(fourthAddress), 30.seconds)
      }

      runOn(node_B_2) {
        enterBarrier("down-fourth-node")
      }

      runOn(node_A_2, node_A_3, node_B_1) {
        enterBarrier("down-fourth-node")

        awaitMembersUp(numberOfMembers = 4, canNotBePartOfMemberRing = Set(fourthAddress), 30.seconds)
      }

      enterBarrier("await-completion-1")
    }

    "be able to DOWN a 'middle' node that is UNREACHABLE" taggedAs LongRunningTest in {
      val secondAddress = address(node_B_1)

      enterBarrier("before-down-second-node")
      runOn(node_A_1) {
        // kill 'second' node
        testConductor.exit(node_B_1, 0).await
        enterBarrier("down-second-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(secondAddress)

        // --- HERE THE LEADER SHOULD DETECT FAILURE AND AUTO-DOWN THE UNREACHABLE NODE ---

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(secondAddress), 30.seconds)
      }

      runOn(node_B_1) {
        enterBarrier("down-second-node")
      }

      runOn(node_A_2) {
        enterBarrier("down-second-node")

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(secondAddress), 30 seconds)
      }

      runOn(node_A_3) {
        enterBarrier("down-second-node")

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(secondAddress), 30 seconds)
      }

      enterBarrier("await-completion-2")
    }

    "NOT be able to DOWN a 'middle' node that is UNREACHABLE" taggedAs LongRunningTest in {
      val thirdAddress = address(node_A_3)

      enterBarrier("before-down-third-node")
      runOn(node_A_1) {
        // kill 'third' node
        testConductor.exit(node_A_3, 0).await
        enterBarrier("down-third-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(thirdAddress)

        // ROLE LEADER SHOULD NOT DOWN UNREACHABLE MEMBER WITH DIFFERENT TARGET ROLE

        remainMembersUnreachable(numberOfMembers = 3, unreachableMember = Set(thirdAddress), 10 seconds)
      }

      runOn(node_A_2) {
        enterBarrier("down-third-node")
        remainMembersUnreachable(numberOfMembers = 3, unreachableMember = Set(thirdAddress), 10 seconds)
      }

      runOn(node_A_3) {
        enterBarrier("down-third-node")
      }

      enterBarrier("await-completion-3")
    }
  }

}
