package kjetland.akkaHttpTools.core.restClient

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import akka.http.scaladsl.model._
import akka.pattern.CircuitBreaker
import akka.stream.{ActorMaterializer, BufferOverflowException}
import com.typesafe.scalalogging.Logger
import kjetland.akkaHttpTools.core._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

abstract class RestClientHelper
(
  val system:ActorSystem,
) extends JacksonJsonSupport {

  implicit val _system = system
  implicit val _ec = system.dispatcher

  val breaker:CircuitBreaker

  implicit val mat:ActorMaterializer = ActorMaterializer()

  val requestLog: Logger = Logger(getClass.getName + ".request")
  val responseLog: Logger = Logger(getClass.getName + ".response")

  private val breakerDecideIfFailure: Try[_] ⇒ Boolean = {
    case _: Success[_] ⇒ false
    case f: Failure[_] =>

      f.exception match {
        case e:BufferOverflowException =>
          // This happens when we're trying to create more requests than
          // configured with:
          // akka.http.host-connection-pool.max-connections
          // akka.http.host-connection-pool.max-open-requests
          false

        case e:HttpErrorExceptionLike =>
          e.circuitBreakerError
        case _ =>
          // default as error
          true
      }
  }

  lazy val maxOpenRequestsLimiter = new OpenRequestLimiter(system, _ec, getClass.getSimpleName)

  private def fixHttpResponseEntityForLogging(res:HttpResponse):Future[HttpResponse] = {
    var isResponseLogging: Boolean = false
    responseLog.whenDebugEnabled {
      isResponseLogging = true
    }

    val futureRes: Future[HttpResponse] = if (isResponseLogging) {
      // If entity is chunked, we must read it here so that we can log it,
      // then transform it into strict so that it can be used again
      res.entity match {
        case chuncked: Chunked =>
          res.entity.toStrict(4000.millis).map(e => res.withEntity(e))
        case _ => Future.successful(res)
      }
    } else {
      // Use entity s is
      Future.successful(res)
    }

    futureRes
  }

  private def doLogResponse(res:HttpResponse): Unit = {
    responseLog.whenDebugEnabled {
      val entityToLog:Any = res.entity match {
        case e:HttpEntity.Strict =>
          e.data.utf8String
        case x => x
      }
      responseLog.debug("response: " + res.withEntity("") + " entity: " + entityToLog)
    }
  }

  def doRequest[T](request: HttpRequest)(entityHandler: ResponseEntity => Future[T]): Future[T] = {


    requestLog.debug("request: " + request)

    val func = internalPerformRequest(request)
      .flatMap ( res => fixHttpResponseEntityForLogging(res) )
      .flatMap { res =>

        responseLog.whenDebugEnabled {
          doLogResponse(res)
        }

        res.status match {
          case OK =>
            entityHandler.apply(res.entity)
          case _ =>
            errorHandler(res)
        }
      }

    breaker.withCircuitBreaker( func, breakerDecideIfFailure)
  }

  def internalPerformRequest(request: HttpRequest): Future[HttpResponse] = {
    maxOpenRequestsLimiter.execute {
      Http().singleRequest(request)
    }
  }

  def doRequestMaybe[T](request:HttpRequest)(entityHandler:ResponseEntity => Future[T]):Future[Option[T]] = {

    requestLog.debug("request: " + request)

    val func = internalPerformRequest(request).flatMap ( res => fixHttpResponseEntityForLogging(res) )
      .flatMap { res =>

        responseLog.whenDebugEnabled {
          doLogResponse(res)
        }

        res.status match {
          case OK =>
            entityHandler.apply(res.entity)
              .map(Option(_))
          case NotFound =>
            Future.successful(None)
          case _ =>
            errorHandler(res)
        }
      }

    breaker.withCircuitBreaker( func, breakerDecideIfFailure )

  }

  def logBodyAndFail[T](entity:ResponseEntity):Future[T] = {
    getBody(entity).map {
      body =>
        throw new Exception("Sopping request: " + body)
    }
  }

  def getBody(entity:ResponseEntity):Future[String] = {
    entity.toStrict(2000.millis)
      .map {
        e =>
          e.data.utf8String
      }
  }

  def errorHandler[T](response:HttpResponse):Future[T] = {
    getBody(response.entity)
      .map { body =>
        response.status match {
          case StatusCodes.Unauthorized => throw UnauthorizedException(body)
          case StatusCodes.Forbidden => throw ForbiddenException()
          case _ =>

            // Try to extract some p
            val e = resolveExceptionFromError(response.status.intValue(), body).getOrElse {
              throw HttpErrorException(response.status, response.status.toString() + " - " + body)
            }

            throw e
        }

      }
  }

  // Can be overridden
  def resolveExceptionFromError(errorCode:Int, errorBody:String):Option[Exception] = None

}

