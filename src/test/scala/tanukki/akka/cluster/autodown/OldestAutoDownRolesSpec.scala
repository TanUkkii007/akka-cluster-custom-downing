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
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.duration._
import scala.collection.immutable

object OldestAutoDownRolesSpec {
  val testRole = Set("testRole")

  val memberA = TestMember(Address("akka.tcp", "sys", "a", 2552), Up, testRole)
  val memberB = TestMember(Address("akka.tcp", "sys", "b", 2552), Up, testRole)
  val memberC = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, testRole)
  val memberD = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, Set("otherRole"))

  val initialMembersByAge = immutable.SortedSet(memberA, memberB, memberC, memberD)(Member.ageOrdering)

  class OldestAutoDownRolesTestActor(address: Address,
                                     targetRoles:              Set[String],
                                     autoDownUnreachableAfter: FiniteDuration,
                                     probe:                    ActorRef)
    extends OldestAutoDownRolesBase(None, targetRoles, autoDownUnreachableAfter) {

    override def selfAddress = address
    override def scheduler: Scheduler = context.system.scheduler

    override def down(node: Address): Unit = {
      if (isOldestOf(None))
        probe ! DownCalled(node)
      else
        probe ! "down must only be done by oldest member"
    }

  }
}

class OldestAutoDownRolesSpec extends AkkaSpec(ActorSystem("OldestAutoDownRolesSpec")) {
  import OldestAutoDownRolesSpec._

  def autoDownActor(autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(new OldestAutoDownRolesTestActor(initialMembersByAge.head.address, testRole, autoDownUnreachableAfter, testActor)))

  def autoDownActorOf(address: Address, autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(classOf[OldestAutoDownRolesTestActor], address, testRole, autoDownUnreachableAfter, testActor))

  "LeaderAutoDownRoles" must {

    "down unreachable when oldest" in {
      val a = autoDownActor(Duration.Zero)
      val second = initialMembersByAge.drop(1).head
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(second)
      expectMsg(DownCalled(second.address))
    }

    "not down unreachable when not oldest" in {
      val second = initialMembersByAge.drop(1).head
      val a = autoDownActorOf(second.address, Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(initialMembersByAge.head)
      expectNoMsg(1.second)
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
      expectNoMsg(1.second)
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
      expectNoMsg(3.second)
    }

    "not down unreachable when there is a Down member" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      val secondIsDown = (initialMembersByAge - second) + second.copy(Down)
      a ! CurrentClusterState(members = secondIsDown)
      a ! UnreachableMember(third)
      expectNoMsg(3.second)
    }

    "not down unreachable that is in Down state" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(second.copy(Down))
      expectNoMsg(3.second)
      // and not down unreachable when there is a Down member
      a ! UnreachableMember(third)
      expectNoMsg(3.second)
    }

    "down unreachable when Down members are removed" in {
      val a = autoDownActor(2.seconds)
      val second = initialMembersByAge.drop(1).head
      val third = initialMembersByAge.drop(2).head
      val secondIsDown = (initialMembersByAge - second) + second.copy(Down)
      a ! CurrentClusterState(members = secondIsDown, unreachable = Set(third))
      expectNoMsg(3.second)
      a ! MemberRemoved(second.copy(Removed), Down)
      expectMsg(DownCalled(third.address))
    }

    "not down when unreachable become reachable inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(memberB)
      a ! ReachableMember(memberB)
      expectNoMsg(3.second)
    }

    "not down when unreachable is removed inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(memberB)
      a ! MemberRemoved(memberB.copy(Removed), previousStatus = Exiting)
      expectNoMsg(3.second)
    }

    "not down when unreachable is already Down" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(memberB.copy(Down))
      expectNoMsg(1.second)
    }

    /*-------------------------------------------------------------------*/

    "not down unreachable with different role" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAge)
      a ! UnreachableMember(memberD)
      expectNoMsg(1.second)
    }
  }
}
