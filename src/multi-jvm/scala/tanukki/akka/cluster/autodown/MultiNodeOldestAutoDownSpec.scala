package tanukki.akka.cluster.autodown

import akka.cluster.{Member, MemberStatus, MultiNodeClusterSpec}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{STMultiNodeSpec, MultiNodeSpec}
import akka.testkit.LongRunningTest
import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration._


class OldestAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode1 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = true))
class OldestAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode2 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = true))
class OldestAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode3 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = true))
class OldestAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode4 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = true))
class OldestAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode5 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = true))

class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode1 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false))
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode2 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false))
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode3 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false))
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode4 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false))
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode5 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false))

abstract class MultiNodeOldestAutoDownSpec(multiNodeConfig: MultiNodeOldestAutoDownSpecConfig) extends MultiNodeSpec(multiNodeConfig)
with STMultiNodeSpec with MultiNodeClusterSpec {
  import multiNodeConfig._

  muteMarkingAsUnreachable()

  "The oldest member in a 5 node cluster" must {

    "be able to DOWN a 'last' node that is UNREACHABLE" taggedAs LongRunningTest in {
      awaitClusterUp(nodeA, nodeB, nodeC, nodeD, nodeE)

      val second = membersByAge.slice(1, 2).head
      val secondRole = roleByMember(second)
      val third = membersByAge.slice(2, 3).head
      val thirdRole = roleByMember(third)
      val forth = membersByAge.slice(3, 4).head
      val forthRole = roleByMember(forth)
      val fifth = membersByAge.last
      val fifthRole = roleByMember(fifth)
      val oldest = roleByMember(membersByAge.head)

      enterBarrier("before-exit-fourth-node")
      runOn(nodeA) {
        // kill 'fifth' node
        testConductor.exit(fifthRole, 0).await
        enterBarrier("down-fifth-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(fifthRole)

        awaitMembersUp(numberOfMembers = 4, canNotBePartOfMemberRing = Set(fifthRole), 30.seconds)
      }

      runOn(fifthRole) {
        enterBarrier("down-fifth-node")
      }

      runOn(secondRole, thirdRole, forthRole) {
        enterBarrier("down-fifth-node")

        awaitMembersUp(numberOfMembers = 4, canNotBePartOfMemberRing = Set(fifthRole), 30.seconds)
      }

      enterBarrier("await-completion-1")
    }

    "be able to DOWN a 'middle' node that is UNREACHABLE" taggedAs LongRunningTest in {
      val second = membersByAge.slice(1, 2).head
      val secondRole = roleByMember(second)
      val third = membersByAge.slice(2, 3).head
      val thirdRole = roleByMember(third)
      val forth = membersByAge.slice(3, 4).head
      val forthRole = roleByMember(forth)
      val oldest = roleByMember(membersByAge.head)

      enterBarrier("before-down-second-node")
      runOn(nodeA) {
        // kill 'second' node
        testConductor.exit(secondRole, 0).await
        enterBarrier("down-second-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(secondRole)

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(secondRole), 30.seconds)
      }

      runOn(secondRole) {
        enterBarrier("down-second-node")
      }

      runOn(thirdRole, forthRole) {
        enterBarrier("down-second-node")

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(secondRole), 30 seconds)
      }

      enterBarrier("await-completion-2")
    }

    "DOWN oldest when oldest alone is unreachable" taggedAs LongRunningTest ignore {
      val second = membersByAge.slice(1, 2).head
      val secondRole = roleByMember(second)
      val third = membersByAge.slice(2, 3).head
      val thirdRole = roleByMember(third)
      val oldest = roleByMember(membersByAge.head)

      enterBarrier("before-down-third-node")
      runOn(nodeA) {
        // kill 'oldest' node
        // ToDo: somehow TestConductor fails with java.lang.IllegalStateException: TestConductorServer was not started
        //testConductor.exit(oldest, 0).await
        enterBarrier("down-oldest-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(oldest)

        awaitMembersUp(numberOfMembers = 2, canNotBePartOfMemberRing = Set(oldest), 30 seconds)
      }

      runOn(thirdRole) {
        enterBarrier("down-oldest-node")

        awaitMembersUp(numberOfMembers = 2, canNotBePartOfMemberRing = Set(oldest), 30 seconds)
      }

      runOn(oldest) {
        enterBarrier("down-oldest-node")
      }

      enterBarrier("await-completion-3")
    }

    "DOWN whole cluster when oldest is down" taggedAs LongRunningTest in {
      val second = membersByAge.slice(1, 2).head
      val secondRole = roleByMember(second)
      val third = membersByAge.slice(2, 3).head
      val thirdRole = roleByMember(third)
      val oldest = roleByMember(membersByAge.head)

      enterBarrier("before-down-third-node")

      if (failureDetectorPuppet) {

        runOn(secondRole) {
          // kill 'oldest' node
          //testConductor.exit(oldest, 0).await
          enterBarrier("down-oldest-node")

          // mark the node as unreachable in the failure detector
          markNodeAsUnavailable(oldest)

          awaitAssert(
            clusterView.isTerminated should be(true),
            10 seconds,
            1 second
          )

        }

        runOn(thirdRole) {
          enterBarrier("down-oldest-node")

          awaitAssert(
            clusterView.isTerminated should be(true),
            10 seconds,
            1 second
          )
        }

        runOn(oldest) {
          enterBarrier("down-oldest-node")
        }
      }

      enterBarrier("await-completion-3")
    }
  }

  def membersByAge: SortedSet[Member] = immutable.SortedSet(clusterView.members.toSeq: _*)(Member.ageOrdering)

  def roleByMember(member: Member): RoleName = roles.find(r => address(r) == member.address).get

  def isFirst(roleName: RoleName): Boolean = address(roleName) == address(nodeA)

}

