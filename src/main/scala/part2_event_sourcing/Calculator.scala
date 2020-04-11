package part2_event_sourcing

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

object Calculator {
  val props: Props = Props(new Calculator)
  val name = "calculator-id"

  sealed trait Command
  final case class Add(value: Double) extends Command
  final case class Subtract(value: Double) extends Command
  final case class Divide(value: Double) extends Command
  final case class Multiple(value: Double) extends Command
  case object Clear extends Command
  case object Print extends Command
  case object GetResult extends Command

  sealed trait Event
  final case class Added(value: Double) extends Event
  final case class Subtracted(value: Double) extends Event
  final case class Divided(value: Double) extends Event
  final case class Multiplied(value: Double) extends Event
  case object Reset extends Event

  final case class CalculationResult(result: Double = 0) {
    def reset: CalculationResult = copy(result = 0)
    def add(value: Double): CalculationResult = copy(result = result + value)
    def subtract(value: Double): CalculationResult = copy(result = result - value)
    def divide(value: Double): CalculationResult = copy(result = result / value)
    def multiply(value: Double): CalculationResult = copy(result = result * value)
  }
}

class Calculator extends PersistentActor with ActorLogging {
  import Calculator._

  override def persistenceId: String = name

  var state: CalculationResult = CalculationResult()

  override def receiveCommand: Receive = {
    case Add(value) => persist(Added(value))(updateState)
    case Subtract(value) => persist(Subtracted(value))(updateState)
    case Divide(value) => persist(Divided(value))(updateState)
    case Multiple(value) => persist(Multiplied(value))(updateState)
    case Clear => persist(Reset)(updateState)
    case Print => println(state.result)
    case GetResult => sender() ! state.result
  }

  override def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case RecoveryCompleted => log.info("Calculator recovery completed")
  }

  private def updateState: Event => Unit = {
    case Reset => state = state.reset
    case Added(value) => state = state.add(value)
    case Subtracted(value) => state = state.subtract(value)
    case Divided(value) if value != 0 => state = state.divide(value)
    case Multiplied(value) => state = state.multiply(value)
  }
}
