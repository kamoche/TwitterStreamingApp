package actors

import actors.StatisticsProvider.{ReviveStorage, ServiceUnavailable}
import actors.TweetReachComputer.ComputeReach
import actors.UserFollowersCounter.{TwitterRateLimitReached, UserFollowersCounterAvailable, UserFollowersCounterUnavailable}
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import javax.inject.Inject
import org.joda.time.{DateTime, Interval}
import play.api.Configuration
import play.api.libs.ws.WSClient
import reactivemongo.core.errors.ConnectionException

import scala.concurrent.duration._

class StatisticsProvider @Inject()(config:Configuration, wSClient: WSClient) extends Actor with ActorLogging {

  import StatisticsProvider._

  var reachComputer: ActorRef = _
  var storage: ActorRef = _
  var followersCounter: ActorRef = _

  implicit val ec = context.dispatcher

  override def preStart(): Unit = {
    log.info("Starting StatisticsProvider")
    followersCounter = context.actorOf(Props[UserFollowersCounter], name = "userFollowersCounter")
    storage = context.actorOf(Props[Storage], name = "storage")
    reachComputer = context.actorOf(TweetReachComputer.props(wSClient,config ,followersCounter, storage), name = "tweetReachComputer")

    context.watch(storage)
  }

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 2.minutes) {
      case _: ConnectionException =>
        Restart
      case t: Throwable =>
        super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
    }

  def receive = {
    case reach: ComputeReach =>
      log.info("Forwarding ComputeReach message to the reach computer")
      reachComputer forward reach
    case Terminated(terminatedStorageRef) =>
      context.system.scheduler.scheduleOnce(1.minute, self, ReviveStorage)
      context.become(storageUnavailable)
    case TwitterRateLimitReached(reset) =>
      context.system.scheduler.scheduleOnce(
        new Interval(DateTime.now, reset).toDurationMillis.millis,
        self,
        ResumeService
      )
      context.become(serviceUnavailable)
    case UserFollowersCounterUnavailable =>
      context.become(followersCountUnavailable)
  }

  def storageUnavailable: Receive = {
    case ComputeReach(_) =>
      sender() ! ServiceUnavailable
    case ReviveStorage =>
      storage = context.actorOf(Storage.props(), Storage.name)
      context.unbecome()
  }

  def serviceUnavailable: Receive = {
    case reach: ComputeReach =>
      sender() ! ServiceUnavailable
    case ResumeService =>
      context.unbecome()
  }

  def followersCountUnavailable: Receive = {
    case UserFollowersCounterAvailable =>
      context.unbecome()
    case reach: ComputeReach =>
      sender() ! ServiceUnavailable
  }

  override def unhandled(message: Any): Unit = {
    log.warning("Unhandled message {} from sender {}", message, sender())
    super.unhandled(message)
  }
}

object StatisticsProvider {
  def props(config:Configuration, wSClient: WSClient): Props = Props(new StatisticsProvider(config,wSClient)).withDispatcher("control-aware-dispatcher")

  val name = "statisticProvider"


  case object ServiceUnavailable
  case object ReviveStorage
  case object ResumeService
}


