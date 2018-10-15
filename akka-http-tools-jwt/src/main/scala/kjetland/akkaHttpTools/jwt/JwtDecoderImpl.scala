package kjetland.akkaHttpTools.jwt

import java.util.concurrent.Executors

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken._
import kjetland.akkaHttpTools.core.Logging
import kjetland.akkaHttpTools.jwt.internal.PublicKeyResolver

import scala.concurrent.{ExecutionContext, Future}

class JwtDecoderImpl
(
  jwkProviderUrl:String, // https://kjetland.eu.auth0.com
  objectMapper:ObjectMapper
) extends JwtDecoder with Logging {

  // This should be a dedicated executionContext - since we sometimes are doing blocking calls to auth0 to fetch keys
  val blockingEC:ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  val jwkProvider = new JwkProviderBuilder(jwkProviderUrl).build()

  val keyResolver = new PublicKeyResolver(jwkProvider)

  val jwtParser:JwtParser = Jwts.parser()
    .setSigningKeyResolver( new PublicKeyResolver(jwkProvider))

  override def decodeAndVerify(_encodedJwtToken: String): Future[JwtClaims] = {

    val encodedJwtToken:String = if ( _encodedJwtToken.startsWith("Bearer ")) {
      _encodedJwtToken.substring(7)
    } else {
      _encodedJwtToken
    }

    Future {
      log.debug(s"decodeAndVerify encodedJwtToken: $encodedJwtToken")
      // Strange java to scala workaround
      val jwt:Jwt[JwsHeader[_], Claims] = jwtParser.parseClaimsJws(encodedJwtToken).asInstanceOf[Jwt[JwsHeader[_], Claims]]
      val claims:Claims = jwt.getBody

      val jwtClaims = JwtClaims(
        iss = claims.get("iss", classOf[String]),
        sub = claims.get("sub", classOf[String]),
        aud = claims.get("aud", classOf[String]),
        iat = claims.get("iat", classOf[Integer]).toInt,
        exp = claims.get("exp", classOf[Integer]).toInt,
        azp = claims.get("azp", classOf[String]),
        scope = claims.get("scope", classOf[String]).split(' ').toSet,
        gty = claims.get("gty", classOf[String]),
      )

      log.debug(s"decodeAndVerify encodedJwtToken: $encodedJwtToken - result: jwtClaims: $jwtClaims")

      jwtClaims
    }(blockingEC)
  }
}
