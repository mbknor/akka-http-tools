package kjetland.akkaHttpTools.jwt

import kjetland.akkaHttpTools.core.ForbiddenException

/*
 {
  "iss": "https://kjetland.eu.auth0.com/",
  "sub": "9GmdDm18EJ9USu6Ki9Qmm3OSWjBrae25@clients",
  "aud": "http://sumo-api-product-admin",
  "iat": 1538639428,
  "exp": 1538725828,
  "azp": "9GmdDm18EJ9USu6Ki9Qmm3OSWjBrae25",
  "scope": "all-orders-read",
  "gty": "client-credentials"
}
 */
case class JwtClaims
(
  iss:String,
  sub:String,
  aud:String,
  iat:Int,
  exp:Int,
  azp:String,
  scope:Set[String],
  gty:String,
  allClaims:Map[String, Set[String]]
) {

  def getClaim(name:String):Set[String] = {
    allClaims.getOrElse(name, Set())
  }

  @throws[ForbiddenException]
  def assertClaimValue(name:String, value:String):Unit = {
    if (!getClaim(name).contains(value)) {
      throw ForbiddenException(s"Claim '$name'=='$value' missing")
    }
  }



}
