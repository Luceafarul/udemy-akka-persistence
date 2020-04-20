package part2_event_sourcing

import akka.actor.ActorSystem
import part2_event_sourcing.snapshots.Basket
import part2_event_sourcing.snapshots.Basket.{GetItems, Item, Items}

class BasketSpec extends PersistenceSpec(ActorSystem("basket-test"))
  with PersistenceCleanup {

  val shopperId = 2L
  val macbookPro = Item("Apple MacBook Pro", 1, BigDecimal(2499.99))
  val macPro = Item("Apple Mac Pro", 1, BigDecimal(10499.99))
  val displays = Item("Apple 4K Display", 2, BigDecimal(2499.99))
  val mxMaster3 = Item("Logitech MX Master 3", 2, BigDecimal(99.99))
  val mxKeys = Item("Logitech MX Keys", 2, BigDecimal(99.99))
  val dWave = Item("D-Wave One", 1, BigDecimal(14999999.99))

  "The Basket" should {
    "skip basket events that occurred before Cleared during recovery" in {
      val basket = system.actorOf(Basket.props, Basket.name(shopperId))

      basket ! Basket.Add(macbookPro, shopperId)
      basket ! Basket.Add(displays, shopperId)
      basket ! Basket.GetItems(shopperId)
      expectMsg(Items(macbookPro, displays))

      basket ! Basket.Clear(shopperId)

      basket ! Basket.Add(macPro, shopperId)
      basket ! Basket.RemoveItem(macPro.productId, shopperId)
      expectMsg(Some(Basket.ItemRemoved(macPro.productId)))

      basket ! Basket.Clear(shopperId)
      basket ! Basket.Add(macbookPro, shopperId)
      basket ! Basket.Add(mxMaster3, shopperId)
      basket ! Basket.Add(mxKeys, shopperId)

      basket ! GetItems(shopperId)
      expectMsg(Items(macbookPro, mxMaster3, mxKeys))

      killActors(basket)

      val basketRestored = system.actorOf(Basket.props, Basket.name(shopperId))
      basketRestored ! Basket.GetItems(shopperId)
      expectMsg(Items(macbookPro, mxMaster3, mxKeys))

      basketRestored ! Basket.CountRecoveredEvents(shopperId)
      expectMsg(Basket.RecoveredEventsCount(3))

      killActors(basketRestored)
    }
  }
}
