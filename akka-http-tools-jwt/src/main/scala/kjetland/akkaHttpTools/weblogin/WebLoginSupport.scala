package kjetland.akkaHttpTools.weblogin

import java.math.BigInteger
import java.security.SecureRandom

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{HttpCookie, RawHeader}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import scala.concurrent.duration._
import kjetland.akkaHttpTools.core._
import StatusCodes._
import scala.concurrent.Future

case class WebLoginSupportConfig
(
  domain:String,
  clientId:String,
  clientSecret:String,
  audience:String,
  scope:String,
  callbackUrlScheme:String
)

trait WebLoginSupport extends Directives with JacksonJsonSupport {

  def system:ActorSystem

  private implicit val _system = system
  private implicit val _ec = system.dispatcher
  private implicit val _mat = ActorMaterializer()

  def webLoginSupportConfig:WebLoginSupportConfig

  private def resolveCallbackUrl(config:WebLoginSupportConfig, _url:String):String = {

    var i = _url.lastIndexOf('/')
    var url = _url.substring(0, i) // Remove last part of ulr
    // replace scheme
    i = url.indexOf("://")
    url = url.substring(i)
    url = s"${config.callbackUrlScheme}${url}/loginCallback"

    url
  }

  def login = {
    path("login") {
      get {
        extractUri { _fullExternalUrl =>

          // Generate random state parameter
          object RandomUtil {
            private val random = new SecureRandom()

            def alphanumeric(nrChars: Int = 24): String = {
              new BigInteger(nrChars * 5, random).toString(32)
            }
          }

          val config = webLoginSupportConfig

          val domain = config.domain
          val clientId = config.clientId
          val callbackUrl = resolveCallbackUrl(config, _fullExternalUrl.toString())
          val audience = config.audience
          val state = RandomUtil.alphanumeric()

          // TODO: Cache state

          val auth0LoginUrl: Uri = Uri(s"$domain/authorize").withRawQueryString(s"client_id=$clientId&redirect_uri=$callbackUrl&response_type=code&scope=${config.scope}&audience=$audience&state=$state")
          //println(s"login redirect: $auth0LoginUrl")
          redirect(auth0LoginUrl, StatusCodes.TemporaryRedirect)
        }
      }
    }
  }

  def loginCallback = {
    path("loginCallback") {
      get {
        extractUri { _fullExternalUrl =>
          parameterMap { allQueryParams =>
            val code = allQueryParams("code")
            val state = allQueryParams("state")
            // Todo: check that state is in cache

            val config = webLoginSupportConfig

            val callbackUrl = resolveCallbackUrl(config, _fullExternalUrl.toString())

            // Fetch token via code

            val fetchTokenRequest = FetchTokenRequest(
              config.clientId,
              config.clientSecret,
              callbackUrl,
              code,
              grant_type = "authorization_code",
              config.audience
            )

            val tokenFuture: Future[String] = Marshal(fetchTokenRequest).to[RequestEntity].flatMap { requestEntity =>
              val request = HttpRequest(method = HttpMethods.POST, uri = s"${config.domain}/oauth/token", entity = requestEntity)
              Http().singleRequest(request).flatMap { response =>
                response.status match {
                  case OK =>
                    Unmarshal(response).to[FetchTokenResponse].map { fetchTokenResponse: FetchTokenResponse =>
                      //println("response: " + fetchTokenResponse)
                      fetchTokenResponse.id_token
                    }
                  case s =>
                    errorHandler(response)
                }

              }
            }

            onSuccess(tokenFuture) { token =>
              setCookie(HttpCookie("authorization", token)) {
                complete("You are now logged in (using cookie)")
              }
            }
          }
        }
      }
    }
  }

  private def getBody(entity:ResponseEntity):Future[String] = {
    entity.toStrict(2000.millis)
      .map {
        e =>
          e.data.utf8String
      }
  }

  private def errorHandler[T](response:HttpResponse):Future[T] = {
    getBody(response.entity)
      .map { body =>
        response.status match {
          case StatusCodes.Unauthorized => throw UnauthorizedException(body)
          case StatusCodes.Forbidden => throw ForbiddenException()
          case _ =>

            throw HttpErrorException(response.status, response.status.toString() + " - " + body)
        }

      }
  }

}

private case class FetchTokenRequest
(
  client_id:String,
  client_secret:String,
  redirect_uri:String,
  code:String,
  grant_type:String,
  audience:String
)

private case class FetchTokenResponse
(
  id_token:String,
  access_token:String
)

