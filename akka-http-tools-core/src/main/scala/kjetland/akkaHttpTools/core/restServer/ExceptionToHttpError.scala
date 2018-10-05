package kjetland.akkaHttpTools.core.restServer

import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{complete, extractUri}
import akka.http.scaladsl.server.ExceptionHandler
import akka.pattern.CircuitBreakerOpenException
import com.typesafe.scalalogging.Logger
import kjetland.akkaHttpTools.core.HttpErrorExceptionLike

import scala.concurrent.TimeoutException

trait ExceptionToHttpError {

  private val log:Logger = Logger(getClass)

  def logAllExceptions:Boolean = true

  // To improve logging, we would like to extract userId from url,
  // either from path, or from queryParam
  def extractUserIdFromUri(uri:Uri):Option[Long]

  def logErrorRequest(uri:Uri, httpErrorCode:Int, errorMsg:String, exception:Option[Exception] = None): Unit = {
    if (logAllExceptions) {

      val userIdString:String = extractUserIdFromUri(uri).map { userId =>
        s"userId: $userId "
      }.getOrElse("")

      val logMsg = s"request failed ${userIdString}url=${uri.toString()} httpCode: $httpErrorCode error: $errorMsg"
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
