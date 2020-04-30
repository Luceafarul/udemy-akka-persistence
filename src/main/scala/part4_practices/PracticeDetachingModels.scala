package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection._

object PracticeDetachingModels extends App {
  class CouponManager extends PersistentActor with ActorLogging {
    import DomainModel._

    val usedCoupons = mutable.Map.empty[String, User]

    override def persistenceId: String = "coupon-manager-id"

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        val code = coupon.code
        if (!usedCoupons.contains(code)) {
          persist(CouponApplied(code, user)) { event =>
            log.info(s"Received $event")
            usedCoupons.put(code, user)
          }
        }
    }

    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) =>
        log.info(s"Restored $event")
        usedCoupons.put(code, user)
    }
  }

  val system = ActorSystem("detach-models-system", ConfigFactory.load().getConfig("detachModels"))
  val couponManager = system.actorOf(Props[CouponManager], "coupon-manager")

  import DomainModel._
  for (i <- 11 to 15) {
    val coupon = Coupon(s"MEGA_DISCOUNT_$i", 100)
    val user = User(s"USER-$i", s"Some $i", s"user_$i@somail.com")

    couponManager ! ApplyCoupon(coupon, user)
  }
}

object DomainModel {
  final case class User(id: String, name: String, email: String)
  final case class Coupon(code: String, promotionAmount: Int)

  // Command
  final case class ApplyCoupon(coupon: Coupon, user: User)

  // Event
  final case class CouponApplied(code: String, user: User)
}

object DataModel {
  final case class WrittenCouponApplied(code: String, id: String, email: String)
  final case class WrittenCouponAppliedV2(code: String, id: String, name: String, email: String)
}

class ModelAdapter extends EventAdapter {
  import DataModel._
  import DomainModel._

  override def manifest(event: Any): String = "CMA"

  override def toJournal(event: Any): Any = event match {
    case event @ CouponApplied(code, user) =>
      println(s"Convert $event into DATA model")
      WrittenCouponAppliedV2(code, user.id, user.name, user.email)
  }

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event @ WrittenCouponApplied(code, id, email) =>
      println(s"Convert $event into DOMAIN model")
      EventSeq.single(CouponApplied(code, User(id, "", email)))
    case event @ WrittenCouponAppliedV2(code, id, name, email) =>
      println(s"Convert new $event into DOMAIN model")
      EventSeq.single(CouponApplied(code, User(id, name, email)))
    case other =>
      EventSeq.single(other)
  }
}
