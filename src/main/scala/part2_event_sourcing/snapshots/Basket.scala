package part2_event_sourcing.snapshots

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

object Basket {
  def props = Props(new Basket)
  def name(shopperId: Long) = s"basket_$shopperId"

  sealed trait Command
  final case class Add(item: Item, shopperId: Long) extends Command
  final case class RemoveItem(productId: String, shopperId: Long) extends Command
  final case class UpdateItem(productId: String, number: Int, shopperId: Long) extends Command
  final case class Clear(shopperId: Long) extends Command
  final case class Replace(items: Items, shopperId: Long) extends Command
  final case class GetItems(shopperId: Long) extends Command

  final case class CountRecoveredEvents(shopperId: Long) extends Command
  final case class RecoveredEventsCount(count: Long)

  sealed trait Event extends Serializable
  final case class Added(item: Item) extends Event
  final case class ItemRemoved(productId: String) extends Event
  final case class ItemUpdated(productId: String, number: Int) extends Event
  final case class Replaced(items: Items) extends Event
  final case class Cleared(clearedItems: Items) extends Event

  final case class Snapshot(items: Items)

  final case class Item(productId: String, number: Int, unitPrice: BigDecimal)

  final case class Items(items: List[Item]) {
    def add(item: Item): Items = copy(items :+ item)
    def remove(productId: String): Items = copy(items.filterNot(_.productId == productId))
    def update(productId: String, number: Int): Items = copy(items.find(_.productId == productId) match {
      case None => items
      case Some(item) => items.filterNot(_.productId == productId) :+ item.copy(number = number)
    })
    def replace(newItems: Items): Items = {
      def loop(origin: List[Item], replace: List[Item]): List[Item] = {
        if (replace.isEmpty) origin
        else {
          origin.find(_.productId == replace.head.productId) match {
            case None => loop(origin, replace.tail)
            case Some(item) =>
              loop(origin.filterNot(_.productId == item.productId), replace.tail)
          }
        }
      }
      def newOrigin: List[Item] = loop(items, newItems.items)
      copy(newOrigin ++ newItems.items)
    }
    def clear(clearItems: Items): Items = Items()
    def containsProduct(productId: String): Boolean = items.exists(_.productId == productId)
  }
  object Items {
    def apply(items: Item*): Items = Items(items.toList)
  }
}

class Basket extends PersistentActor with ActorLogging {
  import Basket._

  var items: Items = Items(List.empty)
  var nrEventsRecovered = 0

  override def persistenceId: String = self.path.name

  override def receiveCommand: Receive = {
    case Add(item, _) =>
      persist(Added(item))(updateState)
    case RemoveItem(id, _) =>
      if (items.containsProduct(id)) persist(ItemRemoved(id)) { removed =>
        updateState(removed)
        sender() ! Some(removed)
      }
      else sender() ! None
    case UpdateItem(id, number, _) =>
      if (items.containsProduct(id)) persist(ItemUpdated(id, number)) { updated =>
        updateState(updated)
        sender() ! Some(updated)
      }
      else sender() ! None
    case Replace(items, _) =>
      persist(Replaced(items))(updateState)
    case Clear(_) =>
      persist(Cleared(items)) { event =>
        updateState(event)
        saveSnapshot(Snapshot(items))
        // TODO why if log delete BasketSpec was failed???
        log.info("Snapshot created...")
      }
    case GetItems(_) =>
      sender() ! items
    case CountRecoveredEvents(_) =>
      sender() ! RecoveredEventsCount(nrEventsRecovered)
    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Snapshot saved with metadata: $metadata")
    case SaveSnapshotFailure(metadata, reason) =>
      log.info(s"Failed to save snapshot: $metadata, because: $reason")
  }

  override def receiveRecover: Receive = {
    case event: Event =>
      nrEventsRecovered += 1
      log.info(s"Restore event: $event, events count: $nrEventsRecovered")
      updateState(event)
    case SnapshotOffer(_, snapshot: Snapshot) =>
      log.info(s"Recovering baskets from snapshot: $snapshot for $persistenceId")
      items = snapshot.items
  }

//  override def recovery: Recovery = Recovery(replayMax = 9L)

  private def updateState: Event => Unit = {
    case Added(item) => items = items.add(item)
    case ItemRemoved(id) => items = items.remove(id)
    case ItemUpdated(id, number) => items = items.update(id, number)
    case Replaced(newItems) => items = items.replace(newItems)
    case Cleared(clearedItems) => items = items.clear(clearedItems)
  }
}
