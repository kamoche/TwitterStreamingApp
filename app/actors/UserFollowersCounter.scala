package actors

import akka.actor.{Actor, ActorLogging, Props}

class UserFollowersCounter extends Actor with ActorLogging{
  override def receive: Receive = ???
}

object UserFollowersCounter {
  def props():Props = Props[UserFollowersCounter]
  val name = "userFollowersCounter"

  case class FollowerCount(tweetId: BigInt,user: String, followersCount: Int)
  case class FetchFollowerCount(tweetId: BigInt, userId: BigInt)
}
