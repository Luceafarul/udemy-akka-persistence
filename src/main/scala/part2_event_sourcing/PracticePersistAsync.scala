package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

object PracticePersistAsync extends App {
  final case class Command(message: String)
  final case class Event(message: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef): Props = Props(new CriticalStreamProcessor(eventAggregator))
  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def persistenceId: String = "critical-stream-processor"

    override def receiveCommand: Receive = {
      case Command(message) =>
        eventAggregator ! s"Processing $message"
        persistAsync(Event(message)) { e =>
          eventAggregator ! e
        }

        // Some actual computation
        val processedMessage = message + "_processed"
        persistAsync(Event(processedMessage)) { e =>
          eventAggregator ! e
        }
    }

    override def receiveRecover: Receive = {
      case message => log.info(s"Recovered successful: $message")
      case RecoveryCompleted => log.info("Recovery completed")
    }
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Aggregate: $message")
    }
  }

  val system = ActorSystem("persist-async-system")
  val eventAggregator = system.actorOf(Props[EventAggregator], "event-aggregator")
  val streamProcessor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "stream-processor")

  streamProcessor ! Command("Command #1")
  streamProcessor ! Command("Command #2 ")
}
