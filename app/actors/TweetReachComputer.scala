package actors

import actors.Storage.{ReachStored, StoreReach}
import actors.TweetReachComputer.{ComputeReach, TweetReach}
import actors.UserFollowersCounter._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.json.JsArray
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import akka.pattern.pipe
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.util.control.NonFatal
//148

class TweetReachComputer @Inject()(wc: WSClient, config: Configuration, userFollowersCounter: ActorRef, storage: ActorRef) extends Actor with ActorLogging {

  implicit val executionContext = context.dispatcher

  var followersCountsByRetweet = Map.empty[FetchedRetweets, List[FollowerCount]]

  override def receive: Receive = {

    case ComputeReach(tweetId) =>
      log.info("Starting to compute tweet reach for tweet {}", tweetId)
      val originalSender = sender()
      fetchRetweets(tweetId, sender()).recover {
        case NonFatal(t) =>
          RetweetFetchingFailed(tweetId, t, originalSender)
      } pipeTo self

    case fetchedRetweets: FetchedRetweets =>
      log.info("Received retweets for tweet {}", fetchedRetweets.tweetId)
      followersCountsByRetweet += fetchedRetweets -> List.empty
      fetchedRetweets.retweeters.foreach { rt =>
        userFollowersCounter ! FetchFollowerCount(fetchedRetweets.tweetId, rt)
      }

    case count@FollowerCount(tweetId, _, _) =>
      log.info("Received followers counts for user {}", tweetId)
      fetchRetweetsFor(tweetId).foreach { fetchedRetweets =>
        updateFollowersCount(tweetId, fetchedRetweets, count)
      }

    case ReachStored(tweetId) =>
      followersCountsByRetweet.keys.find(_.tweetId == tweetId).foreach {
        key => followersCountsByRetweet = followersCountsByRetweet.filterNot(_._1 == key)
      }


  }


  case class FetchedRetweets(tweetId: BigInt, retweeters: List[BigInt], client: ActorRef)

  case class RetweetFetchingFailed(tweetId: BigInt, cause: Throwable, client: ActorRef)

 def credentials: Option[(ConsumerKey,RequestToken)]= for {
    apiKey <- config get[String] "twitter.apiKey"
    apiSecret <- config get[String] "twitter.apiSecret"
    token <- config get[String] "twitter.token"
    tokenSecret <- config get[String] "twitter.tokenSecret"
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

  def fetchRetweets(tweetId: BigInt, client: ActorRef): Future[FetchedRetweets] =
    credentials.map {
      case (consumerKey, requestToken) =>
        wc.url("https://api.twitter.com/1.1/statuses/retweeters/ids.json")
          .sign(OAuthCalculator(consumerKey,requestToken))
          .addQueryStringParameters("id" -> tweetId.toString)
          .addQueryStringParameters("stringify_ids" -> "true")
          .get().map { response =>
          if (response.status == 200) {
            val ids = (response.json \ "ids").as[JsArray].value.map(v => BigInt(v.as[String])).toList
            FetchedRetweets(tweetId, ids, client)
          } else {
            throw new RuntimeException("Could not retrive details for twee")
          }
    }
    }.getOrElse{
      Future.failed(new RuntimeException("You did not correctly configure twitter credentials"))
    }


  def fetchRetweetsFor(tweetId: BigInt) = followersCountsByRetweet.keys.find(_.tweetId == tweetId)

  def updateFollowersCount(tweetId: BigInt, fetchedRetweets: FetchedRetweets, count: FollowerCount) = {
    val existingCount = followersCountsByRetweet(fetchedRetweets)
    followersCountsByRetweet = followersCountsByRetweet.updated(fetchedRetweets, count :: existingCount)
    val newCounts = followersCountsByRetweet(fetchedRetweets)

    if (newCounts.length == fetchedRetweets.retweeters.length) {
      log.info("Received all retweeters followers count for tweet {}, computing sum", tweetId)
      val score = newCounts.map(_.followersCount).sum
      fetchedRetweets.client ! TweetReach(tweetId, score)
      storage ! StoreReach(tweetId, score)
    }

  }
}

object TweetReachComputer {
  def props(wSClient: WSClient,configuration: Configuration, followersCounter: ActorRef, storage: ActorRef): Props =
    Props(new TweetReachComputer(wSClient,configuration, followersCounter, storage))

  val name = "tweetReachComputer"

  case class ComputeReach(tweetId: BigInt)

  case class TweetReach(tweetId: BigInt, score: Int)

}

