package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object PracticeEventAdapters extends App {

  /**
    * Store for acoustic guitars
    */

  // Guitar types
  val ACOUSTIC = "acoustic"
  val ELECTRIC = "electric"

  // Model
  final case class Guitar(id: String, model: String, make: String, guitarType: String = ACOUSTIC)

  // Command
  final case class AddGuitar(guitar: Guitar, quantity: Int)

  final case object Print

  // Event
  final case class GuitarAdded(id: String, model: String, make: String,  quantity: Int)
  final case class GuitarAddedV2(id: String, model: String, make: String, guitarType: String,  quantity: Int)

  class InventoryManager extends PersistentActor with ActorLogging {
    val inventory: mutable.Map[Guitar, Int] = mutable.Map.empty[Guitar, Int].withDefaultValue(0)

    override def persistenceId: String = "inventory-manager-actor"

    override def receiveCommand: Receive = {
      case AddGuitar(guitar @ Guitar(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, guitarType, quantity)) { _ =>
          updateInventory(guitar, quantity)
          log.info(s"Persisted: $quantity x $guitar")
        }
      case Print =>
        log.info(s"Current inventory: $inventory")
    }

    override def receiveRecover: Receive = {
//      case event @ GuitarAdded(id, model, make, quantity) =>
//        log.info(s"Restored: $event")
//        updateInventory(Guitar(id, model, make, ACOUSTIC), quantity)
      case event @ GuitarAddedV2(id, model, make, guitarType, quantity) =>
        log.info(s"Restored: $event")
        updateInventory(Guitar(id, model, make, guitarType), quantity)
    }

    private def updateInventory(guitar: Guitar, quantity: Int): Unit = {
      // val guitar = Guitar(addEvent.id, addEvent.model, addEvent.make)
      inventory.update(guitar, inventory(guitar) + quantity)
    }
  }

  class GuitarReadEventAdapter extends ReadEventAdapter {
    /**
      * Journal -> (send string of bytes) -> serialize -> read event adapter -> Actor
      * (bytes from cassandra)                 (GA)             (GAV2)     (receiveRecover)
      */
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(id, model, make, quantity) =>
        EventSeq.single(GuitarAddedV2(id, model, make, ACOUSTIC, quantity))
      case other => EventSeq.single(other)
    }
  }

  /**
    * WriteEventAdapter
    * Use for backwards compatibility
    * Actor -> write event adapter -> serialize -> Journal
    *
    * Just extends EventAdapter and implement both methods: fromJournal and toJournal
    */

  val system = ActorSystem("eventAdapters", ConfigFactory.load().getConfig("eventAdapters"))
  val inventoryManager = system.actorOf(Props[InventoryManager], "inventory-manager")

  val guitars = for (i <- 1 to 10) yield Guitar(s"$i", s"Urban #$i", "NonStop")

//  guitars.foreach { guitar =>
//    inventoryManager ! AddGuitar(guitar, 5)
//    inventoryManager ! AddGuitar(guitar, 2)
//  }

  println(guitars)

  inventoryManager ! Print
}
