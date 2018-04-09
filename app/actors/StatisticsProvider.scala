package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.ws.WSClient

class StatisticsProvider @Inject()(config:Configuration, wSClient: WSClient) extends Actor with ActorLogging {

  var followerCounter: ActorRef = _
  var tweetComputer: ActorRef = _
  var storage: ActorRef = _

  override def preStart(): Unit = {
    log.info("Starting StatisticsProvider")
    followerCounter = context.actorOf(UserFollowersCounter.props(), TweetReachComputer.name)
    tweetComputer = context.actorOf(TweetReachComputer.props(wSClient,config,followerCounter,storage), TweetReachComputer.name)
    storage = context.actorOf(Storage.props(), Storage.name)
  }

  override def receive: Receive = {
    case message: String =>
  }

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} from sender {}", message, sender())
    super.unhandled(message)
  }
}

object StatisticsProvider {
  def props(): Props = Props(new StatisticsProvider())

  val name = "statisticProvider"
}


