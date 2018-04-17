package actors

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator, RequestToken}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future

trait TwitterCredentials{
  def config: Configuration
  def credentials : Option[(ConsumerKey, RequestToken)] = Some((
    ConsumerKey(config.get[String]("twitter.apiKey"),config.get[String]("twitter.apiSecret"))),
    RequestToken(config.get[String]("twitter.token"),config.get[String]("twitter.tokenSecret")))
}

trait TwitterService {
  def config1: Configuration
  def credentials: Option[(ConsumerKey, RequestToken)]
  def credentials1: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- config1.getString("twitter.apiKey")
    apiSecret <- config1.getString("twitter.apiSecret")
    token <- config1.getString("twitter.token")
    tokenSecret <- config1.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))


  def fetchRetweets(tweetId: BigInt): Future[WSResponse]
}

class TwitterServiceImpl @Inject()(wc: WSClient, config: Configuration) extends TwitterService {

  override val credentials: Option[(ConsumerKey,RequestToken)]= for {
    apiKey <- config get[String] "twitter.apiKey"
    apiSecret <- config get[String] "twitter.apiSecret"
    token <- config get[String] "twitter.token"
    tokenSecret <- config get[String] "twitter.tokenSecret"
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

  override def fetchRetweets(tweetId: BigInt): Future[WSResponse] = credentials.map {
    case (consumerKey, requestToken) =>
      wc.url("https://api.twitter.com/1.1/statuses/retweeters/ids.json")
        .sign(OAuthCalculator(consumerKey,requestToken))
        .addQueryStringParameters("id" -> tweetId.toString)
        .addQueryStringParameters("stringify_ids" -> "true")
        .get()
  }.getOrElse{
    Future.failed(new RuntimeException("You did not correctly configure twitter credentials"))
  }

  override def config1: Configuration = config
}