package kjetland.akkaHttpTools.jwt

import java.util.concurrent.Executors

import com.auth0.jwk.{JwkException, JwkProviderBuilder}
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken._
import kjetland.akkaHttpTools.core.{Logging, UnauthorizedException}
import kjetland.akkaHttpTools.jwt.internal.PublicKeyResolver

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import java.lang.{Iterable => JIterable, Integer => JInt, Long => JLong, Double => JDouble}

class JwtDecoderImpl
(
  jwkProviderUrl:String, // https://kjetland.eu.auth0.com
  jwtAudience:String,
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
      try {
        log.debug(s"decodeAndVerify encodedJwtToken: $encodedJwtToken")
        // Strange java to scala workaround
        val jwt: Jwt[JwsHeader[_], Claims] = jwtParser.parseClaimsJws(encodedJwtToken).asInstanceOf[Jwt[JwsHeader[_], Claims]]
        val claims: Claims = jwt.getBody

        val allClaims:Map[String, Set[String]] = claims.keySet().asScala.map { key =>
          val values:Set[String] = claims.get(key) match {
            case s:String => Set(s)
            case l:JIterable[_] => l.asScala.map(_.toString).toSet
            case x:Any => Set(x.toString)
          }

          key -> values

        }.toMap

        val jwtClaims = JwtClaims(
          iss = claims.get("iss", classOf[String]),
          sub = claims.get("sub", classOf[String]),
          aud = claims.get("aud", classOf[String]),
          iat = claims.get("iat", classOf[Integer]).toInt,
          exp = claims.get("exp", classOf[Integer]).toInt,
          azp = claims.get("azp", classOf[String]),
          scope = Option(claims.get("scope", classOf[String])).map(_.split(' ').toSet).getOrElse(Set()),
          gty = claims.get("gty", classOf[String]),
          allClaims = allClaims
        )

        log.debug(s"decodeAndVerify encodedJwtToken: $encodedJwtToken - result: jwtClaims: $jwtClaims")

        if ( jwtClaims.aud != jwtAudience) {
          log.warn(s"decodeAndVerify audience is not $jwtAudience - jwtClaims: $jwtClaims")
          throw UnauthorizedException("Not authorized")
        }

        jwtClaims
      } catch {
        case e:Exception =>
          // Must unwrap
          if ( e.getCause != null ) {
            e.getCause match {
              case e: JwtException =>
                throw createUnauthorizedException(e)
              case e: JwkException =>
                throw createUnauthorizedException(e)
              case _ => throw e
            }
          } else {
            throw e
          }
        case e:JwtException =>
          throw createUnauthorizedException(e)
        case e:JwkException =>
          throw createUnauthorizedException(e)
      }
    }(blockingEC)
  }

  private def createUnauthorizedException(e:Exception):Exception = {
    log.warn("Failed to decodeAndVerify jwtToken: " + e.toString, e)
    UnauthorizedException("Not authorized")
  }
}
