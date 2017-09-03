/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  * 2016- Modified by Yusuke Yasuda
  *
  * original source code is from
  * https://github.com/akka/akka/blob/master/akka-cluster/src/test/scala/akka/cluster/AutoDownSpec.scala
  */

package tanukki.akka.cluster.autodown

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.{Member, TestMember}
import scala.collection.immutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.duration._

object MajorityLeaderAutoDownSpec {
  val testRole = Set("testRole")
  val leaderRole = testRole.head

  val memberA = TestMember(Address("akka.tcp", "sys", "a", 2552), Up, testRole)
  val memberB = TestMember(Address("akka.tcp", "sys", "b", 2552), Up, testRole)
  val memberC = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, testRole)
  val memberD = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, testRole)
  val memberE = TestMember(Address("akka.tcp", "sys", "e", 2552), Up, testRole)

  val initialMembersByAddress = immutable.SortedSet(memberA, memberB, memberC, memberD, memberE)(Member.ordering)

  class MajorityLeaderAutoDownTestActor(address: Address,
                                        majorityRole: Option[String],
                                        autoDownUnreachableAfter: FiniteDuration,
                                        probe: ActorRef)
      extends MajorityLeaderAutoDownBase(majorityRole, true, autoDownUnreachableAfter) {

    override def selfAddress = address
    override def scheduler: Scheduler = context.system.scheduler

    override def down(node: Address): Unit = {
      if (isMajority(majorityRole)) {
        if (majorityRole.fold(isLeader)(isRoleLeaderOf)) {
          probe ! DownCalled(node)
        } else {
          probe ! "down must only be done by quorum leader"
        }
      } else {
        shutdownSelf()
      }
    }

    override def shutdownSelf(): Unit = {
      probe ! ShutDownCausedBySplitBrainResolver
    }
  }
}

class MajorityLeaderAutoDownSpec extends AkkaSpec(ActorSystem("OldestAutoDownRolesSpec")) {
  import MajorityLeaderAutoDownSpec._

  def autoDownActor(autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(new MajorityLeaderAutoDownTestActor(memberA.address, Some(leaderRole), autoDownUnreachableAfter, testActor)))

  def autoDownActorOf(address: Address, autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(new MajorityLeaderAutoDownTestActor(address, Some(leaderRole), autoDownUnreachableAfter, testActor)))

  "MajorityLeaderAutoDown" must {

    "down unreachable when role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      expectMsg(DownCalled(memberB.address))
    }

    "not down unreachable when not role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      a ! UnreachableMember(memberC)
      expectNoMsg(1.second)
    }

    "down unreachable when becoming role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      expectMsg(DownCalled(memberC.address))
    }

    "down unreachable after specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      expectNoMsg(1.second)
      expectMsg(DownCalled(memberB.address))
    }

    "down unreachable when becoming role leader inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      expectNoMsg(1.second)
      expectMsg(DownCalled(memberC.address))
    }

    "not down unreachable when losing role leadership inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      expectNoMsg(3.second)
    }

    "not down when unreachable become reachable inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! ReachableMember(memberB)
      expectNoMsg(3.second)
    }

    "not down when unreachable is removed inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! MemberRemoved(memberB.copy(Removed), previousStatus = Exiting)
      expectNoMsg(3.second)
    }

    "not down when unreachable is already Down" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB.copy(Down))
      expectNoMsg(1.second)
    }

    /*-------------------------------------------------------------------*/

    "down unreachable when part of majority even if member removed" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! MemberRemoved(memberB.copy(Removed), Down)
      a ! UnreachableMember(memberC)
      expectMsg(DownCalled(memberC.address))
    }

    "not down self when removing member" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! MemberRemoved(memberB.copy(Removed), Down)
      a ! MemberRemoved(memberC.copy(Removed), Down)
      a ! MemberRemoved(memberD.copy(Removed), Down)
      expectNoMsg(3.second)
    }

    "down unreachable when in majority if members are unreachable" in {
      val a = autoDownActor(3.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! UnreachableMember(memberC)
      expectNoMsg(2.second)
      expectMsgAllOf(DownCalled(memberB.address), DownCalled(memberC.address))
     }

    "down self when quorum is NOT met via unreachable" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! UnreachableMember(memberB)
      a ! UnreachableMember(memberC)
      Thread.sleep(500)
      a ! UnreachableMember(memberD)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      expectMsgAllOf(ShutDownCausedBySplitBrainResolver)
    }
  }
}
