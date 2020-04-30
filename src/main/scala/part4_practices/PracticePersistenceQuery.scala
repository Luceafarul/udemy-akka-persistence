package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.util.Random

object PracticePersistenceQuery extends App {
  val system = ActorSystem("persistence-query-system", ConfigFactory.load().getConfig("persistenceQuery"))

  // Read journal
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // Give me all persistence IDs
  val persistenceIds = readJournal.persistenceIds()

  // Boilerplate so far
  implicit val materializer = ActorMaterializer()(system)
  persistenceIds.runForeach { persistenceId =>
    println(s"Found persistence ID: $persistenceId")
  }

  // Events by persistence ID
  val events = readJournal.eventsByPersistenceId("coupon-manager-id", 0, Long.MaxValue)
  events.runForeach { event =>
    println(s"Read event: $event")
  }

  // Events by tags
  val genres = Array("pop", "rock", "hip-hop", "juzz", "disco")
  final case class Song(artist: String, title: String, genre: String)

  // Command
  final case class Playlist(songs: List[Song])

  // Event
  final case class PlaylistPurchased(id: Int, songs: List[Song])

  class MusicStoreActor extends PersistentActor with ActorLogging {
    var currentId = 0

    override def persistenceId: String = "music-store-actor-id"

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(currentId, songs)) { event =>
          log.info(s"Received event: $event. User purchased: $songs")
          currentId += 1
        }
    }

    override def receiveRecover: Receive = {
      case event @ PlaylistPurchased(id, _) =>
        currentId = id
        log.info(s"Restored event: $event")
    }
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = "MSA"

    override def toJournal(event: Any): Any = event match {
      case event @ PlaylistPurchased(_, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(event, genres)
      case event => event
    }
  }

  val musicStoreActor = system.actorOf(Props[MusicStoreActor])

  val r = new Random()
  for (_ <- 1 to 10) {
    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(genres.length))
      Song(s"Artist $i", s"Song #$i", randomGenre)
    }

    musicStoreActor ! Playlist(songs.toList)
  }

  val rockPlyalists = readJournal.eventsByTag("rock", Offset.noOffset)
  rockPlyalists.runForeach { event =>
    println(s"Found a playlist with a rock song: $event")
  }
}
