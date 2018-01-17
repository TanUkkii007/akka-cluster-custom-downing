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
import akka.cluster.TestMember
import akka.cluster.MemberStatus.{Down, Exiting, Removed, Up}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._


object RoleLeaderAutoDownRolesSpec {

  val leaderRole = "leaderRole"
  val dataCenterRole = "dc-1"
  val testRole = "testRole"
  val leaderRoles = Set("leaderRole", dataCenterRole)
  val testRoles = Set(testRole, dataCenterRole)

  val roleLeaderA = TestMember(Address("akka.tcp", "sys", "la", 2552), Up, leaderRoles)
  val roleLeaderB = TestMember(Address("akka.tcp", "sys", "lb", 2552), Up, leaderRoles)
  val roleLeaderC = TestMember(Address("akka.tcp", "sys", "lc", 2552), Up, leaderRoles)
  val memberA = TestMember(Address("akka.tcp", "sys", "a", 2552), Up, testRoles)
  val memberB = TestMember(Address("akka.tcp", "sys", "b", 2552), Up, testRoles)
  val memberC = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, testRoles)
  val memberD = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, Set("otherRole", dataCenterRole))

  class RoleLeaderAutoDownRolesTestActor(leaderRole: String,
                                         targetRoles:              Set[String],
                                         autoDownUnreachableAfter: FiniteDuration,
                                         probe:                    ActorRef)
    extends RoleLeaderAutoDownRolesBase(leaderRole, targetRoles, autoDownUnreachableAfter) {

    override def selfAddress = roleLeaderA.address
    override def scheduler: Scheduler = context.system.scheduler

    override def down(node: Address): Unit = {
      if (isRoleLeaderOf(leaderRole))
        probe ! DownCalled(node)
      else
        probe ! "down must only be done by role leader"
    }

  }
}

class RoleLeaderAutoDownRolesSpec extends AkkaSpec(ActorSystem("LeaderAutoDownRolesSpec")) {
  import RoleLeaderAutoDownRolesSpec._

  def autoDownActor(autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(classOf[RoleLeaderAutoDownRolesTestActor], leaderRole, Set(testRole), autoDownUnreachableAfter, testActor))

  "RoleLeaderAutoDownRoles" must {

    "down unreachable when role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      a ! UnreachableMember(memberB)
      expectMsg(DownCalled(memberB.address))
    }

    "not down unreachable when not role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderB.address))
      a ! UnreachableMember(memberC)
      expectNoMessage(1.second)
    }

    "down unreachable when becoming role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderB.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      expectMsg(DownCalled(memberC.address))
    }

    "down unreachable after specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      a ! UnreachableMember(memberB)
      expectNoMessage(1.second)
      expectMsg(DownCalled(memberB.address))
    }

    "down unreachable when becoming role leader inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderB.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      expectNoMessage(1.second)
      expectMsg(DownCalled(memberC.address))
    }

    "not down unreachable when losing role leadership inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderB.address))
      expectNoMessage(3.second)
    }

    "not down when unreachable become reachable inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      a ! UnreachableMember(memberB)
      a ! ReachableMember(memberB)
      expectNoMessage(3.second)
    }

    "not down when unreachable is removed inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      a ! UnreachableMember(memberB)
      a ! MemberRemoved(memberB.copy(Removed), previousStatus = Exiting)
      expectNoMessage(3.second)
    }

    "not down when unreachable is already Down" in {
      val a = autoDownActor(Duration.Zero)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      a ! UnreachableMember(memberB.copy(Down))
      expectNoMessage(1.second)
    }

    /*-------------------------------------------------------------------*/

    "not down unreachable with different role" in {
      val a = autoDownActor(Duration.Zero)
      a ! RoleLeaderChanged(leaderRole, Some(roleLeaderA.address))
      a ! UnreachableMember(memberD)
      expectNoMessage(1.second)
    }
  }
}
