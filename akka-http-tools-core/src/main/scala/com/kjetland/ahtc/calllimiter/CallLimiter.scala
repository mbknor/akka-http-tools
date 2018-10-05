package com.kjetland.ahtc.calllimiter

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.IntUnaryOperator

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import com.kjetland.ahtc.{AhtException, Logging}

import scala.concurrent.{ExecutionContext, Future}

trait CallLimiter {
  def execute[T](f: => Future[T]): Future[T]
}

case class CallLimiterOverloadedException(description:String) extends RuntimeException(description) with AhtException {
  override def httpStatusCode: StatusCode = StatusCodes.ServiceUnavailable
  override def httpErrorBody: String = s"This part of the api is temporary overloaded: $description"
  override def circuitBreakerError: Boolean = false
}

class CallLimiterPercentageImpl
(
  executionContext: ExecutionContext,
  name: String,
  subName: String,
  maxTotalRequests: Int,
  percentage: Int
) extends CallLimiter with Logging {

  implicit val ec = executionContext

  val maxAllowedRequests: Int = (maxTotalRequests * (percentage / 100.0)).toInt

  val logPrefix = s"$name-RequestLimiter-$subName"

  log.info(s"$logPrefix: - percentage: $percentage% - maxAllowedRequests: $maxAllowedRequests (of $maxTotalRequests)")

  val activeRequestsCount = new AtomicInteger(0)

  val incrementFunc = new IntUnaryOperator {
    override def applyAsInt(currentRequestCount: Int): Int = {
      if (currentRequestCount > maxAllowedRequests) {
        log.error(s"$logPrefix: Dropping request. active requests: $currentRequestCount (of $maxAllowedRequests)")
        throw CallLimiterOverloadedException(s"'$name.$subName' is overloaded")
      }
      currentRequestCount + 1
    }
  }

  override def execute[T](f: => Future[T]): Future[T] = {

    // It is not *that important* if we get 1 or 2 more than allowed..

    activeRequestsCount.getAndUpdate(incrementFunc)
    try {
      val r = f.transform { realFutureResult =>

        activeRequestsCount.decrementAndGet()
        // Just pass the result along
        realFutureResult
      }

      r
    } catch {
      case e: Throwable =>
        // This exception is not comming from the future,
        // but from the code trying to build the future.
        activeRequestsCount.decrementAndGet()
        throw e
    }


  }


}
