package kjetland.akkaHttpTools.jwt

import scala.concurrent.Future

trait JwtDecoder {
  def decodeAndVerify(encodedJwtToken:String):Future[JwtClaims]
}
