package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props, Stash, SupervisorStrategy}
import akka.util.Timeout

object PracticeAkkaRecap extends App {
  class SimpleActor extends Actor with ActorLogging with Stash {
    override def receive: Receive = {
      case "create child" =>
        val child = context.actorOf(Props[SimpleActor], "child-actor")
        child ! "Message for child actor"
      case "stash this" => stash()
      case "change handler now" =>
        unstashAll()
        context.become(anotherHandler)
      case "change" => context.become(anotherHandler)
      case message => println(s"I received $message")
    }

    def anotherHandler: Receive = {
      case message => println(s"In another receive handler: $message")
    }

    override def preStart(): Unit = log.info("I am starting...")

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _ => Stop
    }
  }

  /**
    * 1. Actor encapsulation:
    * Can not instantiate actor yourself,
    * need to create actor via an actor system
    */
  val system = ActorSystem("simple-system")
  val simpleActor = system.actorOf(Props[SimpleActor], "simple-actor")

  /**
    * 2. Actor communication:
    * The only way to communicate with actor
    * is send a message to actor (using tell or ask methods)
    */
  simpleActor ! "Simple message!"

  /**
    * 3. Threading and execution:
    * - message are sent asynchronously
    * - many actor (in the millions) can share a few dozen threads
    * - each message is processed/handled atomically
    * - no needs for locks
    */

  /**
    * 4. Changing actor behavior + stashing
    */

  /**
    * 5. Actors can create (spawn) another actors
    */

  /**
    * 6. Akka creates 3 top level actors (guardians):
    * - /system
    * - /user - parent of every single actor that we create
    * - /root - parent of every single actor in actor system
    */

  /**
    * 7. Actors have a defined lifecycle:
    * - started
    * - stopped (can stopping with context.stop, special messages (PoisonPill))
    * - suspended
    * - resumed
    * - restarted
    */

  /**
    * 8. Logging:
    * - mixin ActorLogging trait
    * - create custom logging
    */

  /**
    * 9. Supervision
    * Decides how to parent actors are going to respond
    * to child actor failures
    */

  /**
    * 10. Schedulers
    * Programming something to happen at a defined point in the future
    */
  import scala.concurrent.duration._
  import system.dispatcher
  system.scheduler.scheduleOnce(2 seconds) {
    simpleActor ! "Delayed message..."
  }

  /**
    * 11. Akka patterns:
    * - FSM
    * - ASK pattern
    * - Pipe pattern
    */
  import akka.pattern.ask
  implicit val timeout = Timeout(3 seconds)

  val future = simpleActor ? "Question"

  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "another-simple-actor")
  future.mapTo[String].pipeTo(anotherActor)
}
