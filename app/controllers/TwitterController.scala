package controllers

import actors.StatisticsProvider
import actors.TweetReachComputer.{ComputeReach, TweetReach, TweetReachCouldNotBeComputed}
import akka.actor.ActorSystem
import akka.util.Timeout
import javax.inject.Inject
import play.api.mvc.{AbstractController, ControllerComponents}
import akka.pattern.ask
import execcontexts.CustomerExecutors

import scala.concurrent.duration._

class TwitterController @Inject()(system: ActorSystem, customerExecutors: CustomerExecutors, cc: ControllerComponents) extends AbstractController(cc) {

  implicit val ec = customerExecutors.expensiveCpuOperations

  lazy val statisticsProvider = system.actorSelection("akka://application/user/statisticsProvider")

  def computeReach(tweetId: String) = Action.async {

    implicit val timeout = Timeout(10.minutes)
    val eventuallyReach = statisticsProvider ? ComputeReach(BigInt(tweetId))
    eventuallyReach.map {
      case tr: TweetReach =>
        Ok(tr.score.toString)
      case StatisticsProvider.ServiceUnavailable =>
        ServiceUnavailable("Sorry")
      case TweetReachCouldNotBeComputed =>
        ServiceUnavailable("Sorry")
    }
  }

}
