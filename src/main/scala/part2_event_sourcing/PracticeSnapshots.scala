package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object PracticeSnapshots extends App {

  // Commands
  final case class SendMessage(contents: String) // Message TO contact
  final case class ReceiveMessage(contents: String) // Message FROM contact
  case object ShowLastMessages

  // Events
  final case class SendMessageRecord(id: Int, contents: String)
  final case class ReceiveMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String): Props = Props(new Chat(owner, contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    val MAX_MESSAGES = 10

    // TODO maybe extract it to object?
    var commandsWithoutCheckpoint = 0
    var currentMessageId = 0
    val lastMessages = mutable.Queue.empty[(String, String)]

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceiveMessage(contents) =>
        persist(ReceiveMessageRecord(currentMessageId, contents)) { event =>
          log.info(s"Receive message: $contents")
          maybeReplaceMessage(contact, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case SendMessage(contents) =>
        persist(SendMessageRecord(currentMessageId, contents)) { event =>
          log.info(s"Send message: $contents")
          maybeReplaceMessage(owner, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case ShowLastMessages =>
        log.info(s"Last messages: $lastMessages")
      // Snapshot-related messages
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Saving snapshot successfully: $metadata")
      case SaveSnapshotFailure(metadata, reason) =>
        log.warning(s"Failed to save snapshot: $metadata, reason: $reason ")
    }

    override def receiveRecover: Receive = {
      case ReceiveMessageRecord(id, contents) =>
        log.info(s"Recovered received message $id: $contents")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case SendMessageRecord(id, contents) =>
        log.info(s"Recovered sent message $id: $contents")
        maybeReplaceMessage(owner, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) =>
        log.info(s"Recovering snapshot: $metadata")
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def maybeReplaceMessage(sender: String, contents: String): Unit = {
      if (lastMessages.size >= MAX_MESSAGES) lastMessages.dequeue()
      lastMessages.enqueue((sender, contents))
    }

    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1
      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving checkpoint...")
        saveSnapshot(lastMessages) // ASYNCHRONOUS OPERATION
        commandsWithoutCheckpoint = 0
      }
    }
  }

  val system = ActorSystem("snapshot-demo")
  val chat = system.actorOf(Chat.props("yrslv713", "m73"))

//  for (i <- 1 to 100000) {
//    chat ! ReceiveMessage(s"Akka Rocks $i")
//    chat ! SendMessage(s"Akka Rules $i")
//  }

  /**
    * Snapshots come in.
    * Snapshots pattern:
    * - After each persist, maybe save snapshot (logic is up to you)
    * - If you save snapshot, handle the SnapshotOffer message in receiveRecover method
    * - Handle SaveSnapshotSuccess and SaveSnapshotFailure in receiveCommand method
    * - Profit from the extra speed
    */
  chat ! ShowLastMessages
}
