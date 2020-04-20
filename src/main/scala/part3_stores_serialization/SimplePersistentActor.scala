package part3_stores_serialization

import akka.actor.ActorLogging
import akka.persistence._

object SimplePersistentActor {
  case object Print
  case object Snap
}

class SimplePersistentActor extends PersistentActor with ActorLogging {
  import SimplePersistentActor._

  // Mutable state
  var nMessages = 0

  override def persistenceId: String = "simple-persistent-actor"

  override def receiveCommand: Receive = {
    case Print =>
      log.info(s"I have persisted $nMessages so far")
    case Snap =>
      saveSnapshot(nMessages)
    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Save snapshot was successful! Metadata: $metadata")
    case SaveSnapshotFailure(_, reason) =>
      log.info(s"Save snapshot failed: $reason")
    case message =>
      persist(message) { _ =>
        log.info(s"Persisting $message")
        nMessages += 1
      }
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.info("Recovery done")
    case SnapshotOffer(_, payload: Int) =>
      log.info(s"Recovered snapshot: $payload")
      nMessages = payload
    case message =>
      log.info(s"Recovered: $message")
      nMessages += 1
  }
}