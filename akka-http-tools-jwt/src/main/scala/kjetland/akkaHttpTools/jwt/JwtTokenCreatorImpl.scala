package kjetland.akkaHttpTools.jwt

import java.time.OffsetDateTime

import akka.actor.ActorSystem
import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.{Cache, CachingSettings}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.CircuitBreaker
import com.fasterxml.jackson.databind.ObjectMapper
import kjetland.akkaHttpTools.core.Logging
import kjetland.akkaHttpTools.core.restClient.RestClientHelper
import kjetland.akkaHttpTools.jwt.internal.{OAuthTokenRequest, OAuthTokenResponse}
import org.apache.commons.codec.digest.DigestUtils

import scala.concurrent.duration._
import scala.concurrent.Future

class JwtTokenCreatorImpl
(
  system:ActorSystem,
  jwkProviderUrl:String, // https://kjetland.eu.auth0.com
  audience:String, // http://sumo-api-product-admin
  val objectMapper:ObjectMapper
) extends RestClientHelper(system) with JwtTokenCreator with Logging {

  val _jwkProviderUrl:String = if ( jwkProviderUrl.endsWith("/")) {
    jwkProviderUrl.substring(0, jwkProviderUrl.length-1)
  } else {
    jwkProviderUrl
  }

  val circuitBreaker_resetTimeout = 20.seconds
  val breaker =
    new CircuitBreaker(
      system.scheduler,
      maxFailures = 2,
      callTimeout = 5.seconds,
      resetTimeout = circuitBreaker_resetTimeout)
      .onOpen(circuitBreakerOpen())
      .onClose(circuitBreakerClosed)

  def circuitBreakerOpen(): Unit = log.error(s"JwtTokenCreatorImpl CircuitBreaker is now open and is now failing requests fast. Will close again in ${circuitBreaker_resetTimeout}")
  def circuitBreakerClosed(): Unit = log.info(s"JwtTokenCreatorImpl CircuitBreaker is now closed again and requests will be passed along")

  val cache:Cache[String, OAuthTokenResponse] = {
    val defaultCachingSettings = CachingSettings(system)
    val lfuCacheSettings =
      defaultCachingSettings.lfuCacheSettings
        .withInitialCapacity(10)
        .withMaxCapacity(100)
        .withTimeToLive(10.hours)
        .withTimeToIdle(10.hours.minus(1.seconds))
    val cachingSettings =
      defaultCachingSettings.withLfuCacheSettings(lfuCacheSettings)
    LfuCache(cachingSettings)
  }


  override def getClientCredentialsToken(clientId: String, clientSecret: String): Future[String] = {

    log.debug(s"getting token for clientId: $clientId")

    val key:String = DigestUtils.sha256Hex(s"$clientId|$clientSecret")

    def fetchThroughCache(): Future[OAuthTokenResponse] = {
      cache.getOrLoad(key, { _ =>
        log.debug(s"getting token for clientId: $clientId (cachemiss)")
        internal_getClientCredentialsToken(clientId, clientSecret)
      } )
    }

    val futureTokenResponse:Future[OAuthTokenResponse] = fetchThroughCache()

    futureTokenResponse.flatMap { tokenResonse =>
      // Must validate that token is still valid
      if ( tokenResonse.expiresAt.isBefore( OffsetDateTime.now) ) {
        // expired.. clear cache and fetch again
        log.debug(s"getting token for clientId: $clientId (cached token expired)")
        cache.remove(key)
        fetchThroughCache()
      } else {
        // Still valid
        Future.successful(tokenResonse)
      }
    }.map { tokenResponse =>
      // extract token
      tokenResponse.access_token
    }

  }

  def internal_getClientCredentialsToken(clientId: String, clientSecret: String): Future[OAuthTokenResponse] = {

    val oauthTokenRequest = OAuthTokenRequest(
      grant_type = "client_credentials",
      client_id = clientId,
      client_secret = clientSecret,
      audience
    )

    val url = s"${_jwkProviderUrl}/oauth/token"

    //println(s"url: $url")

    Marshal(oauthTokenRequest).to[RequestEntity].flatMap { requestEntity =>
      doRequest(HttpRequest(uri = url, method = HttpMethods.POST, entity = requestEntity)) { entity =>
        Unmarshal(entity).to[OAuthTokenResponse].map { tokenResponse =>
          // Add timestamp to tokenResponse
          tokenResponse.copy(
            expiresAt = OffsetDateTime.now.plusSeconds(tokenResponse.expires_in - 60)
          )
        }
      }
    }
  }
}
