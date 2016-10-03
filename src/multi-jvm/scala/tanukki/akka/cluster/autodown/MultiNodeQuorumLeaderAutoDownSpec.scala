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


class QuorumLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode1 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = true))
class QuorumLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode2 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = true))
class QuorumLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode3 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = true))
class QuorumLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode4 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = true))
class QuorumLeaderAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode5 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = true))

class QuorumLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode1 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = false))
class QuorumLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode2 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = false))
class QuorumLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode3 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = false))
class QuorumLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode4 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = false))
class QuorumLeaderAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode5 extends MultiNodeQuorumLeaderAutoDownSpec(MultiNodeQuorumLeaderAutoDownSpecConfig(failureDetectorPuppet = false))

abstract class MultiNodeQuorumLeaderAutoDownSpec(multiNodeConfig: MultiNodeQuorumLeaderAutoDownSpecConfig) extends MultiNodeSpec(multiNodeConfig)
with STMultiNodeSpec with MultiNodeClusterSpec {
  import multiNodeConfig._

  muteMarkingAsUnreachable()

  "The quorum leader in a 5 node cluster" must {

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

    "DOWN itself when quorum is broken" taggedAs LongRunningTest in {
      val thirdAddress = address(nodeC)

      enterBarrier("before-down-third-node")
      runOn(nodeA) {
        // kill 'third' node
        testConductor.exit(nodeC, 0).await
        enterBarrier("down-third-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(thirdAddress)

        awaitAssert(
          clusterView.isTerminated should be(true),
          60 seconds,
          1 second
        )
      }

      runOn(nodeD) {
        enterBarrier("down-third-node")

        awaitAssert(
          clusterView.isTerminated should be(true),
          60 seconds,
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
