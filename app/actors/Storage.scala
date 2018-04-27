package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import javax.inject.Inject
import org.joda.time.DateTime
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}
import reactivemongo.api.commands.{LastError, WriteError, WriteResult}
import reactivemongo.core.errors.ConnectionException

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import akka.pattern.pipe
import models.{StoredReach, StoredReachBsonRepo}


class Storage @Inject()(storedReachBsonRepo: StoredReachBsonRepo) extends Actor with ActorLogging {


  import Storage._
  implicit val timeout = 2.minutes
  var currentWrites = Set.empty[BigInt]


  override def postRestart(reason: Throwable): Unit = {
    reason match {
      case e: ConnectionException =>
        storedReachBsonRepo.obtainConnection()
    }
    super.postRestart(reason)
  }

  override def postStop(): Unit = {
    super.postStop()
  }




  implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {
    case StoreReach(tweetId, score) =>
      if (!currentWrites.contains(tweetId)) {
        currentWrites = currentWrites + tweetId
        val originalSender = sender()
        storedReachBsonRepo.addStoredReach(StoredReach(DateTime.now, tweetId, score)).map{
          result => LastStorageError(result,tweetId,originalSender)
        }.recover{
          case _ =>
            currentWrites = currentWrites - tweetId
        } pipeTo self
      }
    case LastStorageError(result,tweetId, client) =>
      result match {
      case lastError : LastError =>
        if(lastError.inError){
          currentWrites = currentWrites - tweetId
        }else{
          client ! ReachStored(tweetId)
        }
      case _ => {}
    }

  }



}

object Storage {
  def props(storedReachBsonRepo: StoredReachBsonRepo): Props = Props(new Storage(storedReachBsonRepo))

  val name = "storage"

  case class StoreReach(tweetId: BigInt, score: Int)

  case class ReachStored(tweetId: BigInt)

  case class LastStorageError(result: WriteResult, tweetId: BigInt, client: ActorRef)


}
