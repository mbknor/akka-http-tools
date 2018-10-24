package kjetland.akkaHttpTools.core

import akka.http.scaladsl.model.{ContentType, ContentTypes, StatusCode, StatusCodes}

// AkkaHttpToolsException
trait HttpErrorExceptionLike {
  def httpStatusCode:StatusCode
  def httpErrorBody:String
  val httpContentType:ContentType.NonBinary = ContentTypes.`text/plain(UTF-8)`
  def circuitBreakerError:Boolean
}

object HttpErrorException {
  def apply(statusCode:StatusCode, body:String, customDescription:Option[String] = None):HttpErrorException = {
    new HttpErrorException(statusCode, body, customDescription)
  }
}

class HttpErrorException(statusCode:StatusCode, body:String, customDescription:Option[String] = None)
  extends RuntimeException(customDescription.getOrElse(s"HTTP-ERROR ${statusCode.intValue()}: $body")) with HttpErrorExceptionLike {
  override def httpStatusCode: StatusCode = statusCode
  override def httpErrorBody: String = body

  override def circuitBreakerError: Boolean = {
    httpStatusCode.intValue() >= 500
  }
}

class CustomHttpErrorException(_statusCode:StatusCode, _msg:String) extends HttpErrorException(_statusCode, _msg, Some(_msg))


case class UnauthorizedException(msg:String) extends RuntimeException(msg) with HttpErrorExceptionLike {
  override def httpStatusCode: StatusCode = StatusCodes.Unauthorized
  override def httpErrorBody: String = msg
  override def circuitBreakerError: Boolean = false
}

case class ForbiddenException(msg:String = "Forbidden") extends RuntimeException(msg) with HttpErrorExceptionLike {
  override def httpStatusCode: StatusCode = StatusCodes.Forbidden
  override def httpErrorBody: String = msg
  override def circuitBreakerError: Boolean = false
}
