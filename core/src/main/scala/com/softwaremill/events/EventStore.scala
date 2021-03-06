package com.softwaremill.events

import java.time.OffsetDateTime

import com.typesafe.scalalogging.StrictLogging
import slick.dbio
import slick.dbio.Effect.{Read, Write}
import slick.dbio.{Streaming, NoStream}

import scala.concurrent.ExecutionContext

trait EventStore {
  def store(event: StoredEvent): dbio.DBIOAction[Unit, NoStream, Write]
  def getAll(until: OffsetDateTime): dbio.DBIOAction[Seq[StoredEvent], Streaming[StoredEvent], Read]
  def getLength(eventTypes: Set[String]): dbio.DBIOAction[Int, NoStream, Nothing]
  def findById(eventId: Long): dbio.DBIOAction[Seq[StoredEvent], Streaming[StoredEvent], Read]
  def findByIdRange(fromEventId: Long, toEventId: Long): dbio.DBIOAction[Seq[StoredEvent], Streaming[StoredEvent], Read]
}

class DefaultEventStore(protected val database: EventsDatabase)(implicit ec: ExecutionContext)
    extends EventStore with SqlEventStoreSchema with StrictLogging {

  import database._
  import database.driver.api._

  def store(event: StoredEvent) = (events += event).map(_ => ())

  def getAll(until: OffsetDateTime) = events.filter(_.created < until).sortBy(_.id.asc).result

  def getLength(eventTypes: Set[String]) = events.map(_.eventType).filter(_.inSet(eventTypes)).length.result

  def findById(eventId: Long) = events.filter(_.id === eventId).result

  def findByIdRange(fromEventId: Long, toEventId: Long) = events.filter(a => a.id between (fromEventId, toEventId)).result
}

trait SqlEventStoreSchema {
  protected val database: EventsDatabase

  import database._
  import database.driver.api._

  protected val events = TableQuery[Events]

  protected class Events(tag: Tag) extends Table[StoredEvent](tag, "events") {
    def id = column[Long]("id")
    def eventType = column[String]("event_type")
    def aggregateType = column[String]("aggregate_type")
    def aggregateId = column[Long]("aggregate_id")
    def aggregateIsNew = column[Boolean]("aggregate_is_new")
    def created = column[OffsetDateTime]("created")
    def userId = column[Long]("user_id")
    def txId = column[Long]("tx_id")
    def eventJson = column[String]("event_json")

    def * = (id, eventType, aggregateType, aggregateId, aggregateIsNew, created, userId, txId, eventJson) <> ((StoredEvent.apply _).tupled, StoredEvent.unapply)
  }
}

