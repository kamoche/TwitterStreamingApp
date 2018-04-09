package controllers

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.oauth._
import play.api.libs.ws
import play.api.libs.ws._
import play.api.mvc._
import play.libs.oauth.OAuth.ServiceInfo

import scala.concurrent.{ExecutionContext, Future}

object routes1 {
  object Application {
    val authenticate = Call("GET", "authenticate")
    val index = Call("GET", "index")
  }
}

class TwitterController @Inject()(wc: WSClient, configuration: Configuration, cc: ControllerComponents)(implicit exec: ExecutionContext) extends AbstractController(cc) {

  val KEY = ConsumerKey(configuration.get[String]("twitter.apiKey"), configuration[String]("twitter.apiSecret"))

  val oauth = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    " https://api.twitter.com/oauth/access_token",
    " https://api.twitter.com/oauth/authorize",
    KEY
  ), true)


  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield
      RequestToken(token, secret)
  }

  def authenticate = Action { request: Request[AnyContent] =>
    request.getQueryString("oauth_verifier").map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      // We got the verifier; now get the access token, store it and back to index
      oauth.retrieveAccessToken(tokenPair, verifier) match {
        case Right(t) => {
          // We received the authorized tokens in the OAuth object - store it before we proceed
          Redirect(routes1.Application.index).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      }
    }.getOrElse(
      oauth.retrieveRequestToken("https://localhost:9000/auth") match {
        case Right(t) => {
          // We received the unauthorized tokens in the OAuth object - store it before we proceed
          Redirect(oauth.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      })
  }

  def timeline = Action.async { implicit request: Request[AnyContent] =>
    sessionTokenPair match {
      case Some(credentials) => {
        wc.url("https://api.twitter.com/1.1/statuses/home_timeline.json")
          .sign(OAuthCalculator(KEY, credentials))
          .get
          .map(result => Ok(result.json))
      }
      case _ => Future.successful(Redirect(routes1.Application.authenticate))
    }
  }

}


