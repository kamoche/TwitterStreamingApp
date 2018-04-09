package actors

import akka.actor.{Actor, ActorLogging, Props}

import scala.concurrent.ExecutionContext

class Storage() extends Actor with ActorLogging{

  val Database = "twitterService"
  val ReachCollection = "ComputedReach"

  implicit val ec: ExecutionContext = context.dispatcher
  override def receive: Receive = ???
}

object Storage {
  def props(): Props = Props[Storage]
  val name = "storage"

  case class StoreReach(tweetId: BigInt, score: Int)
  case class ReachStored(tweetId: BigInt)
}
