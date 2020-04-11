package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

object PracticeRecovery extends App {

  final case class Command(message: String)

  final case class Event(id: Int, message: String)

  class RecoveryActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "recovery-actor"

    override def receiveCommand: Receive = online(1)

    def online(lastEventId: Int): Receive = {
      case Command(message) =>
        persist(Event(lastEventId, message)) { event =>
          log.info(s"Successfully persist: $event, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
          context.become(online(lastEventId + 1))
        }
    }

    override def receiveRecover: Receive = {
      case Event(id, message) =>
        // if (message.contains("321")) throw new RuntimeException("Can't take this anymore!")
        log.info(s"Restore: $message, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")

        /**
          * This will NOT change the event handler during recovering
          * AFTER recovery the "normal" handler will be the result of ALL the stacking of context.become call.
          */
        context.become(online(id + 1))
      case RecoveryCompleted =>
        log.info("Recovery is done")
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error(s"Failure during recovery: ${cause.getMessage}")
      super.onRecoveryFailure(cause, event)
    }

    // override def recovery: Recovery = Recovery(toSequenceNr = 100)
    // override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest )
    // override def recovery: Recovery = Recovery.none
  }

  val system = ActorSystem("recovery-system")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recovery-actor")

  /**
    * Stashing commands
    * ALL COMMANDS SENT DURING THE RECOVERY ARE STASHED
    */
//    for (i <- 1 to 1000) {
//      recoveryActor ! Command(s"Message $i")
//    }

  /**
    * Failure during recovery
    * - onRecoveryFailure + the actor is STOPPED
    */

  /**
    * Customizing recovery
    * - User recovery methods with updater Recovery behavior for debugging
    * - DO NOT persist more events after a customized __INCOMPLETE__ recovery
    */

  /**
    * Recovery status
    * or KNOWING when you're done recovering
    * Methods recoveryFinished and recoveryRunning are not so useful, because
    * - need signal when you'are done recovery
    */

  /**
    * Stateless actors
    */
  recoveryActor ! Command("Special message 1")
  recoveryActor ! Command("Special message 2")
}
