package actors

import com.typesafe.config.Config
import play.api.Configuration
import play.api.libs.oauth.{ConsumerKey, RequestToken}


trait TwitterCredentials {

  def config: Configuration
  def credentials : Option[(ConsumerKey, RequestToken)] = Some((
    ConsumerKey(config.get[String]("twitter.apiKey"),config.get[String]("twitter.apiSecret"))),
    RequestToken(config.get[String]("twitter.token"),config.get[String]("twitter.tokenSecret")))
}
