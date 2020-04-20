package part3_stores_serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

// Command
final case class RegisterUser(email: String, name: String)

// Event
final case class UserRegistered(id: Int, email: String, name: String)

// Serializer
class UserRegistrationSerializer extends Serializer {
  override def identifier: Int = 213321

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event @ UserRegistered(id, email, name) =>
      println(s"Serializing $event")
      s"[$id//$email//$name]".getBytes
    case _ => throw new IllegalArgumentException("Only user registration events supported in this serializer")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length - 1).split("//")
    val id = values(0).toInt
    val email = values(1)
    val name = values(2)
    val result = UserRegistered(id, email, name)
    println(s"Deserialized: $string to $result")
    result
  }

  override def includeManifest: Boolean = false
}

class UserRegistrationActor extends PersistentActor with ActorLogging {
  var currentId = 0;

  override def persistenceId: String = "user-registration"

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) { event =>
        currentId += 1
        log.info(s"Persisted: $event")
      }
  }

  override def receiveRecover: Receive = {
    case event @ UserRegistered(id, _, _) =>
      log.info(s"Recovered: $event")
      currentId = id
  }
}

object PracticeCustomSerialization extends App {
  /**
    * Send command to the actor:
    *   1. Actor calls persist
    *   2. Serializer serializes the event into bytes
    *   3. The journal writes the bytes
    *
    * When the actor does the recovery the opposite happens (3, 2, 1)
    */
  val customSerializerSystem = ActorSystem("customSerializerSystem", ConfigFactory.load().getConfig("customSerializer"))
  val userRegistrationActor = customSerializerSystem.actorOf(Props[UserRegistrationActor], "userRegistrationActor")

  // Comment line below when re-run after first run application
  for (i <- 1 to 10) {
    userRegistrationActor ! RegisterUser(s"simple_email_$i@mail.com", s"User $i")
  }
}
