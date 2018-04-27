package models

import execcontexts.CustomerExecutors
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONBinary, BSONDateTime, BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONHandler, Macros, Subtype}
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.Future

case class StoredReach(when: DateTime, tweetId: BigInt, score: Int)


object BsonFormats{

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    val fmt = ISODateTimeFormat.dateTime()
    def read(time: BSONDateTime) = new DateTime(time.value)
    def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
  }

  implicit object BigIntHandler extends BSONDocumentReader[BigInt] with BSONDocumentWriter[BigInt] {
    def write(bigInt: BigInt) = BSONDocument(
      "signum" -> bigInt.signum,
      "value" -> BSONBinary(bigInt.toByteArray, Subtype.UserDefinedSubtype))

    def read(doc: BSONDocument): BigInt = BigInt(
      doc.getAs[Int]("signum").get, {
        val buf = doc.getAs[BSONBinary]("value").get.value
        buf.readArray(buf.readable())
      })
  }

  implicit def storedReachReader: BSONDocumentReader[StoredReach] = Macros.reader[StoredReach]
  implicit def storedReachWriter: BSONDocumentWriter[StoredReach] = Macros.writer[StoredReach]


}




class StoredReachBsonRepo @Inject()(reactiveMongoApi: ReactiveMongoApi, customerExecutors: CustomerExecutors){

  import BsonFormats._
  implicit val ec = customerExecutors.expensiveDbLookups
  val ReachCollection = "ComputedReach"

  def storedReachCollection: Future[BSONCollection] = reactiveMongoApi.database.map(_.collection(ReachCollection))


  def addStoredReach(storedReach: StoredReach): Future[WriteResult] = {
    storedReachCollection.flatMap(_.insert(storedReach))
  }

  def obtainConnection(): Unit ={
    reactiveMongoApi.database.map(_.collection[BSONCollection](ReachCollection))
  }


}
