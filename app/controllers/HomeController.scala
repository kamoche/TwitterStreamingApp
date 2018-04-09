package controllers

import actors.TwitterCredentials
import javax.inject._
import play.api.Configuration
import play.api.libs.oauth.{ConsumerKey, RequestToken}
import play.api.libs.ws.WSClient
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(wc: WSClient, configuration: Configuration,cc: ControllerComponents) extends AbstractController(cc) with TwitterCredentials{

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  override protected val credentials: (ConsumerKey, RequestToken) = for{
    apiKey <- configuration get[String] "twitter.apiKey"
    apiSecret <- configuration get[String] "twitter.apiSecret"
    token <- configuration get[String] "twitter.token"
    tokenSecret <- configuration get[String] "twitter.tokenSecret"
  } yield (ConsumerKey(apiKey,apiSecret), RequestToken(token, tokenSecret))
}
