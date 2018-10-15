package kjetland.akkaHttpTools.jwt

import scala.concurrent.Future

trait JwtTokenCreator {
  def getClientCredentialsToken(clientId:String, clientSecret:String):Future[String]
}
