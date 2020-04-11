package part2_event_sourcing

import java.util.UUID

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

object PracticePersistentActorsExercise extends App {

  /**
    * Persist actor for a voting station
    * Keep:
    *   - the citizens who voted
    *   - the poll: a mapping between a candidate and the number of received votes
    * The actor must be able to recover its state if it's shutdown or restarted
    */
  final case class Vote(citizenId: UUID, candidate: String)
  case object ShowPoll

  final case class Voted(citizenId: UUID, candidate: String)

  final case class VoteStationState(votedCitizens: List[UUID] = Nil, poll: Map[String, Int] = Map.empty) {
    def update(citizenId: UUID, candidate: String): VoteStationState =
      poll.get(candidate) match {
        case Some(numberOfVotes) => copy(votedCitizens = votedCitizens :+ citizenId, poll = poll + (candidate -> (numberOfVotes + 1)))
        case None => copy(votedCitizens = votedCitizens :+ citizenId, poll = poll + (candidate -> 1))
      }
  }

  /**
    * VoteStation should have internal state:
    *   - citizen who voted
    *   - map candidate -> number of votes
    * Transform Command into Event
    * Recover state
    * State case class vs state in actor?
    */
  class VoteStation extends PersistentActor with ActorLogging {
    private var state = VoteStationState()

    def updateState(event: Voted): Unit =
      state = state.update(event.citizenId, event.candidate)

    override def persistenceId: String = "vote-station-01"

    override def receiveCommand: Receive = {
      case Vote(citizenId, candidate) =>
        if (state.votedCitizens.contains(citizenId)) {
          log.info(s"Citizen: $citizenId voted!")
          sender() ! "Citizen voted!"
        } else {
          persist(Voted(citizenId, candidate)) { event =>
            updateState(event)
          }
        }
      case ShowPoll => println(state.poll)
    }

    override def receiveRecover: Receive = {
      case event: Voted => updateState(event)
      case RecoveryCompleted => log.info(s"VoteStation[$persistenceId] successful recovered!")
    }
  }

  val system = ActorSystem("vote-system")
  val voteStation = system.actorOf(Props[VoteStation], "vote-station-01")
  val citizen01 = UUID.randomUUID()
  val citizen02 = UUID.randomUUID()
  val citizen03 = UUID.randomUUID()

  val candidate01 = "River Song"
  val candidate02 = "Honey Moon"

//  voteStation ! Vote(citizen01, candidate01)
//  voteStation ! Vote(citizen02, candidate01)
//  voteStation ! Vote(citizen03, candidate02)
//  voteStation ! Vote(citizen03, candidate02)
  voteStation ! ShowPoll
}
