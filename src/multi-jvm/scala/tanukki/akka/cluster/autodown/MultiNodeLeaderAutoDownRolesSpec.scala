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


class LeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode1 extends MultiNodeLeaderAutoDownRolesSpec(MultiNodeLeaderAutoDownRolesSpecConfig(failureDetectorPuppet = true))
class LeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode2 extends MultiNodeLeaderAutoDownRolesSpec(MultiNodeLeaderAutoDownRolesSpecConfig(failureDetectorPuppet = true))
class LeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode3 extends MultiNodeLeaderAutoDownRolesSpec(MultiNodeLeaderAutoDownRolesSpecConfig(failureDetectorPuppet = true))
class LeaderDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode4 extends MultiNodeLeaderAutoDownRolesSpec(MultiNodeLeaderAutoDownRolesSpecConfig(failureDetectorPuppet = true))

class LeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode1 extends MultiNodeLeaderAutoDownRolesSpec(MultiNodeLeaderAutoDownRolesSpecConfig(failureDetectorPuppet = false))
class LeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode2 extends MultiNodeLeaderAutoDownRolesSpec(MultiNodeLeaderAutoDownRolesSpecConfig(failureDetectorPuppet = false))
class LeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode3 extends MultiNodeLeaderAutoDownRolesSpec(MultiNodeLeaderAutoDownRolesSpecConfig(failureDetectorPuppet = false))
class LeaderDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode4 extends MultiNodeLeaderAutoDownRolesSpec(MultiNodeLeaderAutoDownRolesSpecConfig(failureDetectorPuppet = false))

abstract class MultiNodeLeaderAutoDownRolesSpec(multiNodeConfig: MultiNodeLeaderAutoDownRolesSpecConfig) extends MultiNodeSpec(multiNodeConfig)
with STMultiNodeSpec with MultiNodeClusterSpec {
  import multiNodeConfig._

  muteMarkingAsUnreachable()

  "The Leader in a 4 node cluster" must {

    "be able to DOWN a 'last' node that is UNREACHABLE" taggedAs LongRunningTest in {
      awaitClusterUp(node_A_1, node_A_2, node_B_1, node_A_3)

      val fourthAddress = address(node_A_3)

      enterBarrier("before-exit-fourth-node")
      runOn(node_A_1) {
        // kill 'fourth' node
        testConductor.exit(node_A_3, 0).await
        enterBarrier("down-fourth-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(fourthAddress)

        // --- HERE THE LEADER SHOULD DETECT FAILURE AND AUTO-DOWN THE UNREACHABLE NODE ---

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(fourthAddress), 30.seconds)
      }

      runOn(node_A_3) {
        enterBarrier("down-fourth-node")
      }

      runOn(node_A_2, node_B_1) {
        enterBarrier("down-fourth-node")

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(fourthAddress), 30.seconds)
      }

      enterBarrier("await-completion-1")
    }

    "be able to DOWN a 'middle' node that is UNREACHABLE" taggedAs LongRunningTest in {
      val secondAddress = address(node_A_2)

      enterBarrier("before-down-second-node")
      runOn(node_A_1) {
        // kill 'second' node
        testConductor.exit(node_A_2, 0).await
        enterBarrier("down-second-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(secondAddress)

        // --- HERE THE LEADER SHOULD DETECT FAILURE AND AUTO-DOWN THE UNREACHABLE NODE ---

        awaitMembersUp(numberOfMembers = 2, canNotBePartOfMemberRing = Set(secondAddress), 30.seconds)
      }

      runOn(node_A_2) {
        enterBarrier("down-second-node")
      }

      runOn(node_B_1) {
        enterBarrier("down-second-node")

        awaitMembersUp(numberOfMembers = 2, canNotBePartOfMemberRing = Set(secondAddress), 30 seconds)
      }

      enterBarrier("await-completion-2")
    }

    "NOT be able to DOWN a 'middle' node that is UNREACHABLE" taggedAs LongRunningTest in {
      val thirdAddress = address(node_B_1)

      enterBarrier("before-down-third-node")
      runOn(node_A_1) {
        // kill 'second' node
        testConductor.exit(node_B_1, 0).await
        enterBarrier("down-third-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(thirdAddress)

        Thread.sleep(5000)

        // --- HERE THE LEADER SHOULD DETECT FAILURE AND AUTO-DOWN THE UNREACHABLE NODE ---

        awaitMembersUp(numberOfMembers = 2, canNotBePartOfMemberRing = Set(), 30.seconds)
      }

      runOn(node_A_2) {
        enterBarrier("down-third-node")

        awaitMembersUp(numberOfMembers = 2, canNotBePartOfMemberRing = Set(), 30 seconds)
      }

      runOn(node_B_1) {
        enterBarrier("down-third-node")
      }

      enterBarrier("await-completion-3")
    }
  }
}
