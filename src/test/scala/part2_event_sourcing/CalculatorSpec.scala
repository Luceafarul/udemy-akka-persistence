package part2_event_sourcing

import akka.actor.ActorSystem

class CalculatorSpec extends PersistenceSpec(ActorSystem("test-persistence"))
  with PersistenceCleanup {

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
