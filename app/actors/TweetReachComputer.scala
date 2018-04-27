package actors

import actors.Storage.{ReachStored, StoreReach}
import actors.TweetReachComputer.{ComputeReach, TweetReach, TweetReachCouldNotBeComputed}
import actors.UserFollowersCounter._
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.json.JsArray
import play.api.libs.oauth.{ OAuthCalculator}
import akka.pattern.pipe
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.control.NonFatal

class TweetReachComputer @Inject()(wc: WSClient, configuration: Configuration, userFollowersCounter: ActorRef, storage: ActorRef) extends Actor
  with ActorLogging with TwitterCredentials {

  implicit val executionContext = context.dispatcher

  override def config: Configuration = configuration


  var followersCountsByRetweet = Map.empty[FetchedRetweets, List[FollowerCount]]

  val retryUnacknowledged: Cancellable = context.system.scheduler.schedule(1.second, 20.seconds,self, ResendUnacknowledged)

  override def postStop(): Unit = {
    retryUnacknowledged.cancel()
    super.postStop()
  }

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

    case FollowerCountUnavailable(tweetId, user) =>
      followersCountsByRetweet.keys.find(_.tweetId == tweetId).foreach { fetchedRetweets =>
        fetchedRetweets.client ! TweetReachCouldNotBeComputed
      }

    case ReachStored(tweetId) =>
      followersCountsByRetweet.keys.find(_.tweetId == tweetId).foreach {
        key => followersCountsByRetweet = followersCountsByRetweet.filterNot(_._1 == key)
      }

    case ResendUnacknowledged =>
      val unacknowledged = followersCountsByRetweet.filterNot{
        case (retweet,counts) =>
          retweet.retweeters.size != counts.size

      }

      unacknowledged.foreach{
        case (retweet, counts) =>
          val score = counts.map(_.followersCount).sum
          storage ! StoreReach(retweet.tweetId, score)
      }

    case RetweetFetchingFailed(tweetId, cause, client) =>
      log.error(cause, "Could not fetch retweets for tweet {}", tweetId)
      client ! TweetReachCouldNotBeComputed


  }

  case object ResendUnacknowledged
  case class FetchedRetweets(tweetId: BigInt, retweeters: List[BigInt], client: ActorRef)

  case class RetweetFetchingFailed(tweetId: BigInt, cause: Throwable, client: ActorRef)


  def fetchRetweets(tweetId: BigInt, client: ActorRef): Future[FetchedRetweets] =
    credentials.map {
      case (consumerKey, requestToken) =>
        wc.url("https://api.twitter.com/1.1/statuses/retweeters/ids.json")
          .sign(OAuthCalculator(consumerKey,requestToken))
          .addQueryStringParameters("id" -> tweetId.toString)
          .addQueryStringParameters("count" -> "100")
          .addQueryStringParameters("stringify_ids" -> "true")
          .get().map { response =>
          if (response.status == 200) {
            val ids = (response.json \ "ids").as[JsArray].value.map(v => BigInt(v.as[String])).toList
            FetchedRetweets(tweetId, ids, client)
          } else {
            throw new RuntimeException(s"Could not retrive details for Tweet $tweetId with response code: ${response.status}, error: ${response}")
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

  case object TweetReachCouldNotBeComputed

}

