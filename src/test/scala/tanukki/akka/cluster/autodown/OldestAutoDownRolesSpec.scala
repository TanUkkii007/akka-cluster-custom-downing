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
import akka.cluster.{MemberStatus, Member, TestMember}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.duration._
import scala.collection.immutable

case class DownCalledBySecondaryOldest(address: Address)

case object ShutDownCausedBySplitBrainResolver

object OldestAutoDownRolesSpec {
  val testRole = Set("testRole", "dc-1")
  val testRoleOpt = Some("testRole")

  val memberA = TestMember(Address("akka.tcp", "sys", "a", 2552), Up, testRole)
  val memberB = TestMember(Address("akka.tcp", "sys", "b", 2552), Up, testRole)
  val memberC = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, testRole)
  val memberD = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, Set("otherRole", "dc-1"))

  val initialMembersByAge = immutable.SortedSet(memberA, memberB, memberC, memberD)(Member.ageOrdering)

  class OldestAutoDownTestActor(address: Address,
                                downIfAlone: Boolean,
                                autoDownUnreachableAfter: FiniteDuration,
                                probe:                    ActorRef)
    extends OldestAutoDownBase(testRoleOpt, downIfAlone, autoDownUnreachableAfter) {

    override def selfAddress = address
    override def scheduler: Scheduler = context.system.scheduler

    override def down(node: Address): Unit = {
      if (isOldestOf(testRoleOpt))
        probe ! DownCalled(node)
      else if (isSecondaryOldest(testRoleOpt))
        probe ! DownCalledBySecondaryOldest(node)
      else
        probe ! "down must only be done by oldest member"
    }

    override def shutdownSelf(): Unit = {
      probe ! ShutDownCausedBySplitBrainResolver
    }
  }
}

class OldestAutoDownRolesSpec extends AkkaSpec(ActorSystem("OldestAutoDownRolesSpec")) {
  import OldestAutoDownRolesSpec._

  def autoDownActor(autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(new OldestAutoDownTestActor(initialMembersByAge.head.address, true, autoDownUnreachableAfter, testActor)))

  def autoDownActorOf(address: Address, autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(new OldestAutoDownTestActor(address, true, autoDownUnreachableAfter, testActor)))

  def autoDownActorOfDownIfAloneFalse(address: Address, autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(new OldestAutoDownTestActor(address, false, autoDownUnreachableAfter, testActor)))

  "OldestAutoDownRoles" must {

    "down unreachable when oldest" in {
      val a = autoDownActor(Duration.Zero)
      val second = initialMembersByAge.drop(1).head
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(second)
      expectMsg(DownCalled(second.address))
    }

    "not down unreachable when not oldest" in {
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      val a = autoDownActorOf(second.address, Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(third)
      expectNoMessage(1.second)
    }

    "down unreachable when becoming oldest" in {
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      val a = autoDownActorOf(second.address, Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! MemberRemoved(initialMembersByAge.head.copy(Removed), Leaving)
      a ! UnreachableMember(third)
      expectMsg(DownCalled(third.address))
    }

    "down unreachable after specified duration" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(second)
      expectNoMessage(1.second)
      expectMsg(DownCalled(second.address))
    }

    "down unreachable when there is an Leaving member" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! MemberLeft(second.copy(Leaving))
      a ! UnreachableMember(third)
      expectMsg(DownCalled(third.address))
    }

    "not down unreachable when there is an Exiting member" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! MemberExited(second.copy(Leaving).copy(Exiting))
      a ! UnreachableMember(third)
      expectNoMessage(3.second)
    }

    "not down unreachable when there is a Down member" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      val secondIsDown = (initialMembersByAge - second) + second.copy(Down)
      a ! CurrentClusterState(members = secondIsDown)
      a ! UnreachableMember(third)
      expectNoMessage(3.second)
    }

    "not down unreachable that is in Down state" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(second.copy(Down))
      expectNoMessage(3.second)
      // and not down unreachable when there is a Down member
      a ! UnreachableMember(third)
      expectNoMessage(3.second)
    }

    "not down unreachable when there is a Down member right after down a member" in {
      val a = autoDownActor(Duration.Zero)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(second)
      expectMsg(DownCalled(second.address))
      a ! UnreachableMember(third)
      expectNoMessage(3.second)
    }

    "down unreachable when Down members are removed" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      val secondIsDown = (initialMembersByAge - second) + second.copy(Down)
      a ! CurrentClusterState(members = secondIsDown, unreachable = Set(third))
      expectNoMessage(3.second)
      a ! MemberRemoved(second.copy(Removed), Down)
      expectMsg(DownCalled(third.address))
    }

    "not down when unreachable become reachable inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(memberB)
      a ! ReachableMember(memberB)
      expectNoMessage(3.second)
    }

    "not down when unreachable is removed inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(memberB)
      a ! MemberRemoved(memberB.copy(Removed), previousStatus = Exiting)
      expectNoMessage(3.second)
    }

    "not down when unreachable is already Down" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(memberB.copy(Down))
      expectNoMessage(1.second)
    }

    /*-------------------------------------------------------------------*/

    "shutdown self when partitioned from oldest" in {
      val oldest = initialMembersByAge.head
      val second = initialMembersByAge.drop(1).head
      val last = initialMembersByAge.last
      val lastActor = autoDownActorOf(last.address, 0.5 second)
      lastActor ! CurrentClusterState(members = initialMembersByAge)
      lastActor ! UnreachableMember(second)
      lastActor ! UnreachableMember(oldest)
      expectMsg(ShutDownCausedBySplitBrainResolver)
    }

    "shutdown secondary oldest itself when partitioned from oldest when `donw-if-alone=false`" in {
      val oldest = initialMembersByAge.head
      val second = initialMembersByAge.drop(1).head
      val secondaryActor = autoDownActorOfDownIfAloneFalse(second.address, 0.5 second)
      secondaryActor ! CurrentClusterState(members = initialMembersByAge)
      secondaryActor ! UnreachableMember(oldest)
      expectMsg(ShutDownCausedBySplitBrainResolver)
    }

    "down self when oldest itself alone is unreachable if `donw-if-alone=true" in {
      val oldest = initialMembersByAge.head
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      val last = initialMembersByAge.last
      val oldestActor = autoDownActor(0.5 second)
      oldestActor ! CurrentClusterState(members = initialMembersByAge)
      oldestActor ! UnreachableMember(second)
      oldestActor ! UnreachableMember(third)
      oldestActor ! UnreachableMember(last)
      expectMsg(ShutDownCausedBySplitBrainResolver)
    }

    "down oldest when oldest alone is unreachable if `donw-if-alone=true" in {
      val oldest = initialMembersByAge.head
      val second = initialMembersByAge.drop(1).head
      val a = autoDownActorOf(second.address, 0.5 second)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(oldest)
      expectMsg(DownCalledBySecondaryOldest(oldest.address))
    }

    "NOT down oldest when younger than second member even if oldest alone is unreachable with `donw-if-alone=true" in {
      val oldest = initialMembersByAge.head
      val third = initialMembersByAge.drop(2).head
      val a = autoDownActorOf(third.address, 0.5 second)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(oldest)
      expectNoMessage(1.second)
    }
  }
}
