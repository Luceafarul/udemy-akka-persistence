package part2_event_sourcing

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.Try

class CalculatorSpec extends TestKit(ActorSystem("test-persistence"))
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  val storageLocations: Seq[File] = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir").map { s =>
    new File(system.settings.config.getString(s))
  }

  override def beforeAll(): Unit =
    storageLocations.foreach(dir => Try(FileUtils.deleteDirectory(dir)))

  override def afterAll(): Unit = {
    storageLocations.foreach(dir => Try(FileUtils.deleteDirectory(dir)))
    TestKit.shutdownActorSystem(system)
  }

  def killActors(actors: ActorRef*): Unit = {
    actors.foreach { actor =>
      watch(actor)
      system.stop(actor)
      expectTerminated(actor)
    }
  }

  "The Calculator" should {
    "recover last know result after crash" in {
      val calc = system.actorOf(Calculator.props, Calculator.name)
      calc ! Calculator.Add(2)
      calc ! Calculator.GetResult
      expectMsg(2)

      calc ! Calculator.Subtract(1.25)
      calc ! Calculator.GetResult
      expectMsg(0.75)

      killActors(calc)

      val calcResurrected = system.actorOf(Calculator.props, Calculator.name)
      calcResurrected ! Calculator.GetResult
      expectMsg(0.75)

      calcResurrected ! Calculator.Add(3)
      calcResurrected ! Calculator.GetResult
      expectMsg(3.75)
    }
  }
}
