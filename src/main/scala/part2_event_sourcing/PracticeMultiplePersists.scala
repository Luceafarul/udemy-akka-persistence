package part2_event_sourcing

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PracticeMultiplePersists extends App {
  /**
    * Diligent accountant: with every invoice, will persist TWO events
    *   - a tax record for the fiscal authority
    *   - an invoice record for personal logs or some auditing authority
    */
  // Commands
  final case class Invoice(recipient: String, date: Date, amount: Double)

  // Events
  final case class TaxRecord(taxId: String, recordId: Int, date: Date, tax: Double, totalAmount: Double)
  final case class InvoiceRecord(recordId: Int, recipient: String, data: Date, amount: Double)

  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef): Props = Props(new DiligentAccountant(taxId, taxAuthority))
  }
  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {
    var taxRecordId = 0
    var invoiceRecordId = 0

    override def persistenceId: String = "diligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        persist(TaxRecord(taxId, taxRecordId, date, amount * 0.05, amount)) { event =>
          taxAuthority ! event
          taxRecordId += 1
          persist("I hereby declare this tax record to be true and completed") { declaration =>
            taxAuthority ! declaration
          }
        }
        persist(InvoiceRecord(invoiceRecordId, recipient, date, amount)) { event =>
          taxAuthority ! event
          invoiceRecordId += 1
          persist("I hereby declare this invoice record to be true") { declaration =>
            taxAuthority ! declaration
          }
        }
    }

    override def receiveRecover: Receive = {
      case event => log.info(s"Recovered: $event")
    }
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Received: $message")
    }
  }

  val system = ActorSystem("multiple-persists-demo")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "HMCK")
  val accountant = system.actorOf(DiligentAccountant.props("UK321312-21323", taxAuthority))

  accountant ! Invoice("The Sofa Company", new Date(), 3000)

  /**
    * The message ordering (TaxRecord -> InvoiceRecord) is GUARANTEED.
    * PERSISTENT IS ALSO BASE ON MESSAGE PASSING.
    */

  /**
    * Nested persist
    */

  accountant ! Invoice("The Supercar Company", new Date(), 73000000)
}
