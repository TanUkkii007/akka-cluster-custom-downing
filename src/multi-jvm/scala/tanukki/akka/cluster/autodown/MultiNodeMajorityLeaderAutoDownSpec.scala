/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  * 2016- modified by Yusuke Yasuda
  * original source code is at https://github.com/akka/akka/blob/master/akka-cluster/src/multi-jvm/scala/akka/cluster/LeaderDowningNodeThatIsUnreachableSpec.scala
  */

package tanukki.akka.cluster.autodown

import akka.cluster.{MemberStatus, MultiNodeClusterSpec}
import akka.remote.testkit.{STMultiNodeSpec, MultiNodeSpec}
import akka.testkit.LongRunningTest
import scala.concurrent.duration._

class MajorityLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode1 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = true))
class MajorityLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode2 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = true))
class MajorityLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode3 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = true))
class MajorityLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode4 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = true))
class MajorityLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode5 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = true))

class MajorityLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode1 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = false))
class MajorityLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode2 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = false))
class MajorityLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode3 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = false))
class MajorityLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode4 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = false))
class MajorityLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode5 extends MultiNodeMajorityLeaderAutoDownSpec(MultiNodeMajorityLeaderAutoDownSpecConfig(failureDetectorPuppet = false))

abstract class MultiNodeMajorityLeaderAutoDownSpec(multiNodeConfig: MultiNodeMajorityLeaderAutoDownSpecConfig) extends MultiNodeSpec(multiNodeConfig)
with STMultiNodeSpec with MultiNodeClusterSpec {
  import multiNodeConfig._

  muteMarkingAsUnreachable()

  "The majority leader in a 5 node cluster" must {

    "be able to DOWN a 'last' node that is UNREACHABLE" taggedAs LongRunningTest in {
      awaitClusterUp(nodeA, nodeB, nodeC, nodeD, nodeE)

      val fifthAddress = address(nodeE)

      enterBarrier("before-exit-fourth-node")
      runOn(nodeA) {
        // kill 'fifth' node
        testConductor.exit(nodeE, 0).await
        enterBarrier("down-fifth-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(fifthAddress)

        // --- HERE THE LEADER SHOULD DETECT FAILURE AND AUTO-DOWN THE UNREACHABLE NODE ---

        awaitMembersUp(numberOfMembers = 4, canNotBePartOfMemberRing = Set(fifthAddress), 30.seconds)
      }

      runOn(nodeE) {
        enterBarrier("down-fifth-node")
      }

      runOn(nodeB, nodeC, nodeD) {
        enterBarrier("down-fifth-node")

        awaitMembersUp(numberOfMembers = 4, canNotBePartOfMemberRing = Set(fifthAddress), 30.seconds)
      }

      enterBarrier("await-completion-1")
    }

    "be able to DOWN a 'middle' node that is UNREACHABLE" taggedAs LongRunningTest in {
      val secondAddress = address(nodeB)

      enterBarrier("before-down-second-node")
      runOn(nodeA) {
        // kill 'second' node
        testConductor.exit(nodeB, 0).await
        enterBarrier("down-second-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(secondAddress)

        // --- HERE THE LEADER SHOULD DETECT FAILURE AND AUTO-DOWN THE UNREACHABLE NODE ---

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(secondAddress), 30.seconds)
      }

      runOn(nodeB) {
        enterBarrier("down-second-node")
      }

      runOn(nodeC, nodeD) {
        enterBarrier("down-second-node")

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(secondAddress), 30 seconds)
      }

      enterBarrier("await-completion-2")
    }

    "DOWN itself when in minority" taggedAs LongRunningTest in {
      val thirdAddress = address(nodeC)
      val fourthAddress = address(nodeD)

      enterBarrier("before-down-third-node")
      runOn(nodeA) {
        // kill 'third' node
        testConductor.exit(nodeC, 0).await
        testConductor.exit(nodeD, 0).await
        enterBarrier("down-third-node")

         // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(thirdAddress)
        markNodeAsUnavailable(fourthAddress)

        awaitAssert(
          clusterView.isTerminated should be(true),
          10 seconds,
          1 second
        )
      }

      runOn(nodeC, nodeD) {
        enterBarrier("down-third-node")

        awaitAssert(
          clusterView.isTerminated should be(true),
          10 seconds,
          1 second
        )
      }

      runOn(nodeB, nodeC, nodeE) {
        enterBarrier("down-third-node")
      }

      enterBarrier("await-completion-3")
    }

  }
}
