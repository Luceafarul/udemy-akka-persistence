package part2_event_sourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PracticePersistentActor extends App {
  /**
    * Scenario: we have business and accountant which keeps track of our invoices
    */

  // COMMAND
  final case class Invoice(recipient: String, date: Date, amount: Int)
  final case class InvoiceBulk(invoices: List[Invoice])

  // SPECIAL COMMAND
  case object Shutdown

  // EVENT
  final case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  class Accountant extends PersistentActor with ActorLogging {
    // TODO temporary variables
    var latestInvoiceId = 0
    var totalAmount = 0

    override def persistenceId: String = "simple-accountant" // BEST PRACTICE: must be unique

    // This is the "normal" receive method
    override def receiveCommand: Receive = {
      /**
        * Pattern when receive a command:
        * 1. Create an EVENT to persist into the store
        * 2. Persist the EVENT, the pass in a callback that will get triggered once the event is written
        * 3. Update actor's state after the event has persisted
        */
      case Invoice(recipient, date, amount) =>
        log.info(s"Receive invoice from: $recipient for amount: $amount")
        val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount)
        persist(event)
        /* HERE WE GET TIME GAP: All other messages sent to this actor are STASHED */
        { e =>
          // SAFE to access mutable state here
          latestInvoiceId += 1
          totalAmount += amount

          // Correctly identify the sender of the COMMAND
          sender() ! "PersistenceACK"
          log.info(s"Persisted $e as invoice #${e.id}, for total amount: $totalAmount")
        }
      /**
        * Pattern when receive a BULK command:
        * 1. Create EVENTS (plural )
        * 2. Persist all the EVENTS
        * 3. Update the actor's state when each event is persisted
        */
      case InvoiceBulk(invoices) =>
        val ids = latestInvoiceId to (latestInvoiceId + invoices.size)

        val events = ids.zip(invoices).map { pair =>
          val (id, invoice) = pair
          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }
        persistAll(events) { e =>
          latestInvoiceId += 1
          totalAmount += e.amount
          log.info(s"Persisted SINGLE $e as invoice #${e.id}, for total amount: $totalAmount")
        }
      case Shutdown => context.stop(self)
      // Act like a normal actor
      case "print" =>
        log.info(s"Latest invoice id: $latestInvoiceId, total amount: $totalAmount")
    }

    // Handler that will be called on recovery
    override def receiveRecover: Receive = {
      /**
        * Best practice: follow the logic in the persist steps of receiveCommand
        */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
        log.info(s"Recovered invoice #$id for amount: $amount, total amount: $totalAmount")
    }

    override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist reject of $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("persistent-system")
  val simpleAccountant = system.actorOf(Props[Accountant], "simpleAccountant")

  for (i <- 1 to 10) {
    simpleAccountant ! Invoice("The Sofa Co.", new Date, i * 10000)
  }

  /**
    * Persistent failures:
    * 1. onPersistFailure - call if persisting of an event failed
    *    The actor will be stopped. Inconsistent event.
    *    Best practice: start an actor again after a while. (Use backoff supervisor)
    * 2. onPersistRejected - called when Journal throw exception while persisting the event
    *    The actor is resumed. Actor state not corrupted in the process.
    */

  /**
    * Persisting multiple events:
    * 1. persitAll
    */
  val invoices = for (n <- 1 to 5) yield Invoice("The awesome chairs", new Date, n * 3000)
//  simpleAccountant ! InvoiceBulk(invoices.toList)

  /**
    * NEVER EVER CALL PERSIST OR PERSISTALL FROM FUTURES!!!
    */

  /**
    * Shutdown of the persistent actors
    * Best practice: define own "shutdown" messages.
    * Because custom message put into normal mail mailbox. PoisonPill - not.
    */

//  simpleAccountant ! PoisonPill
  simpleAccountant ! Shutdown
}
