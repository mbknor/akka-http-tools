package kjetland.akkaHttpTools.core.restServer

import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{complete, extractUri, deleteCookie}
import akka.http.scaladsl.server.ExceptionHandler
import akka.pattern.CircuitBreakerOpenException
import com.typesafe.scalalogging.Logger
import kjetland.akkaHttpTools.core.{HttpErrorExceptionLike, UnauthorizedException}

import scala.concurrent.TimeoutException

trait ExceptionToHttpError {

  private val log:Logger = Logger(getClass)

  def logAllExceptions:Boolean = true

  // Override to include extra info when logging error-requests
  def infoStringFroRequest(uri:Uri):Option[String] = None

  def logErrorRequest(uri:Uri, httpErrorCode:Int, errorMsg:String, exception:Option[Exception] = None): Unit = {
    if (logAllExceptions) {

      val logMsg = s"request failed ${infoStringFroRequest(uri).map(s => s + " ").getOrElse("")}url=${uri.toString()} httpCode: $httpErrorCode error: $errorMsg"
      exception match {
        case Some(e) => log.error(logMsg, e)
        case None    => log.info(logMsg)
      }
    }
  }

  implicit def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case x: CircuitBreakerOpenException =>
        extractUri { uri =>
          logErrorRequest(uri, StatusCodes.ServiceUnavailable.intValue, x.toString)
          complete(HttpResponse(StatusCodes.ServiceUnavailable, entity = "Service is experiencing temporary problems") )
        }

      case x: TimeoutException =>
        extractUri { uri =>
          val errorMsg = "Service is experiencing temporary problems (timeout)"
          logErrorRequest(uri, StatusCodes.ServiceUnavailable.intValue, x.toString)
          complete(HttpResponse(StatusCodes.ServiceUnavailable, entity = errorMsg) )
        }

      case x: UnauthorizedException =>
        extractUri { uri =>
          logErrorRequest(uri, x.httpStatusCode.intValue(), x.toString)
          deleteCookie("authorization") { // Cookie used by kjetland.akkaHttpTools.weblogin.WebLoginSupport
            complete(HttpResponse(x.httpStatusCode, entity = HttpEntity(x.httpContentType, x.httpErrorBody)))
          }
        }


      case x: HttpErrorExceptionLike =>
        extractUri { uri =>
          logErrorRequest(uri, x.httpStatusCode.intValue(), x.toString)
          complete(HttpResponse(x.httpStatusCode, entity = HttpEntity(x.httpContentType, x.httpErrorBody)))
        }

      case e:Exception =>
        extractUri { uri =>
          logErrorRequest(uri, StatusCodes.InternalServerError.intValue, e.toString, Some(e))
          complete(HttpResponse(StatusCodes.InternalServerError, entity = "error"))
        }
    }
}
