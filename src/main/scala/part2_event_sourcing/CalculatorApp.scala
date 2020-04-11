package part2_event_sourcing

import akka.actor.{ActorSystem, Props}

object CalculatorApp extends App {
  val system = ActorSystem("calc")
  val calc = system.actorOf(Props[Calculator], Calculator.name)

  calc ! Calculator.Add(1)
  calc ! Calculator.Add(2)
  calc ! Calculator.Multiple(7)
  calc ! Calculator.Print
}
