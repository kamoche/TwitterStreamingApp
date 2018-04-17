package actors

import actors.Storage.{LastStorageError, ReachStored, StoreReach}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import javax.inject.Inject
import org.joda.time.DateTime
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{LastError, WriteError, WriteResult}
import reactivemongo.core.errors.ConnectionException

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern.pipe
import reactivemongo.bson.{BSONBinary, BSONDateTime, BSONDocument, BSONDocumentReader, BSONDocumentWriter, Subtype}

case class StoredReach(when: DateTime, tweetId: BigInt, score: Int)

class Storage @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends Actor with ActorLogging with ReactiveMongoComponents {

  //  http://reactivemongo.org/releases/0.11/documentation/tutorial/play2.html
  //  https://github.com/ricsirigu/play26-swagger-reactivemongo
  implicit val exec = context.dispatcher
  val Database = "twitterService"
  val ReachCollection = "ComputedReach"
  var connection: MongoConnection = _
  var collection: Future[BSONCollection] = _
  var currentWrites = Set.empty[BigInt]

  obtainConnection()

  override def postRestart(reason: Throwable): Unit = {
    reason match {
      case e: ConnectionException =>
        obtainConnection()
    }
    super.postRestart(reason)
  }

  override def postStop(): Unit = {
    connection.askClose()
    reactiveMongoApi.driver.close()
    super.postStop()
  }


  //  def collection(name:String): Future[BSONCollection] = reactiveMongoApi.database.map{_.collection[BSONCollection](name)}


  //  def collection: BSONCollection = _

  implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {
    case StoreReach(tweetId, score) =>
      if (!currentWrites.contains(tweetId)) {
        currentWrites = currentWrites + tweetId
        val originalSender = sender()

        collection.flatMap {
          _.insert(StoredReach(DateTime.now, tweetId, score)).map{
            result =>  LastStorageError(result,tweetId, originalSender)

          }recover{
            case  _ =>
              currentWrites = currentWrites - tweetId
          }
        } pipeTo self

      }
    case LastStorageError(result,tweetId, client) =>
      //TODO Fix this
      result match {
      case lastError : LastError =>
        if(lastError.inError){
          currentWrites = currentWrites - tweetId
        }else{
          client ! ReachStored(tweetId)
        }
    }

  }


  private def obtainConnection(): Unit = {
    connection = reactiveMongoApi.driver.connection(List("localhost"))
    val db = connection.database(Database)
    collection = db.map(_.collection[BSONCollection](ReachCollection))

  }

}

object Storage {
  def props(): Props = Props[Storage]

  val name = "storage"

  case class StoreReach(tweetId: BigInt, score: Int)

  case class ReachStored(tweetId: BigInt)

  case class LastStorageError(result: WriteResult, tweetId: BigInt, client: ActorRef)

  implicit object BigIntHandler extends BSONDocumentReader[BigInt] with BSONDocumentWriter[BigInt] {
    def write(bigInt: BigInt): BSONDocument = BSONDocument(
      "signum" -> bigInt.signum,
      "value" -> BSONBinary(bigInt.toByteArray, Subtype.UserDefinedSubtype))

    def read(doc: BSONDocument): BigInt = BigInt(
      doc.getAs[Int]("signum").get, {
        val buf = doc.getAs[BSONBinary]("value").get.value
        buf.readArray(buf.readable())
      })
  }

  implicit object StoredReachHandler extends BSONDocumentReader[StoredReach] with BSONDocumentWriter[StoredReach] {
    override def read(bson: BSONDocument): StoredReach = {
      val when = bson.getAs[BSONDateTime]("when").map(t => new DateTime(t.value)).get
      val tweetId = bson.getAs[BigInt]("tweet_id").get
      val score = bson.getAs[Int]("score").get
      StoredReach(when, tweetId, score)
    }

    override def write(r: StoredReach): BSONDocument = BSONDocument(
      "when" -> BSONDateTime(r.when.getMillis),
      "tweetId" -> r.tweetId,
      "tweet_id" -> r.tweetId,
      "score" -> r.score
    )
  }


}
