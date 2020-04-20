package part3_stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import part3_stores_serialization.SimplePersistentActor._

object PracticeLocalStores extends App {
  val localStoresActorSystem = ActorSystem("localStoresSystem", ConfigFactory.load().getConfig("localStores"))
  val simplePersistentActor = localStoresActorSystem.actorOf(Props[SimplePersistentActor], "simple-persistent-actor")

  for (i <- 1 to 10) {
    simplePersistentActor ! s"I love Akka [$i]"
  }

  simplePersistentActor ! Print
  simplePersistentActor ! Snap

  for (i <- 11 to 20) {
    simplePersistentActor ! s"I love Akka [$i]"
  }
}
