package tanukki.akka.cluster.autodown

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}


class AkkaSpec(system: ActorSystem) extends TestKit(system) with WordSpecLike with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
