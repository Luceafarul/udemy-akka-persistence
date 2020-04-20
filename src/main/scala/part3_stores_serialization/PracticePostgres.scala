package part3_stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import part3_stores_serialization.SimplePersistentActor._

object PracticePostgres extends App {
  val postgresActorSystem = ActorSystem("postgresSystem", ConfigFactory.load().getConfig("postgres"))
  val simplePersistentActor = postgresActorSystem.actorOf(Props[SimplePersistentActor], "simple-persistent-actor")

  for (i <- 1 to 10) {
    simplePersistentActor ! s"I love Akka [$i]"
  }

  simplePersistentActor ! Print
  simplePersistentActor ! Snap

  for (i <- 11 to 20) {
    simplePersistentActor ! s"I love Akka [$i]"
  }
}
