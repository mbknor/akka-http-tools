package kjetland.akkaHttpTools.core.rateLimiter

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.pattern.AskTimeoutException
import akka.testkit.TestKitBase
import kjetland.akkaHttpTools.core.testUtils.AwaitHelper
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._

class FutureRateLimiterTest extends FunSuite with TestKitBase with Matchers with BeforeAndAfterAll
  with AwaitHelper {

  lazy implicit val system = ActorSystem("FutureRateLimiterTest")
  lazy implicit val ec = system.dispatcher

  val nextNameCount = new AtomicInteger(0)

  test("rateLimiter - failAllQueuedIfError=false - no errors") {

    val rateLimiter: FutureRateLimiter = new FutureRateLimiterImpl(
      system,
      "rl" + nextNameCount.incrementAndGet(),
      FutureRateLimiterConfig(
        2,
        failAllQueuedIfError = false,
        totalTimeoutForSingleFuture = 30.seconds,
        pause = 0.seconds
      )
    )

    val r1 = rateLimiter.execute { futureFunc(true) }
    val r2 = rateLimiter.execute { futureFunc(true) }
    val r3 = rateLimiter.execute { futureFunc(true) }
    val r4 = rateLimiter.execute { futureFunc(true) }

    assert(await(r1) == true)
    assert(await(r2) == true)
    assert(await(r3) == true)
    assert(await(r4) == true)

  }

  test("rateLimiter - failAllQueuedIfError=false - with errors") {

    val rateLimiter: FutureRateLimiter = new FutureRateLimiterImpl(
      system,
      "rl" + nextNameCount.incrementAndGet(),
      FutureRateLimiterConfig(
        2,
        failAllQueuedIfError = false,
        totalTimeoutForSingleFuture = 30.seconds,
        pause = Duration("80 millis").asInstanceOf[FiniteDuration]
      )
    )

    val r1 = rateLimiter.execute { futureFunc(true) }
    val r2 = rateLimiter.execute { futureFunc(false) }
    val r3 = rateLimiter.execute { futureFunc(true) }
    val r4 = rateLimiter.execute { futureFunc(true) }
    val r5 = rateLimiter.execute { futureFunc(false) }

    assert(await(r1) == true)
    intercept[MyFutureError](await(r2))
    assert(await(r3) == true)
    assert(await(r4) == true)
    assert(await(r4) == true)
    intercept[MyFutureError](await(r5))
  }

  test("rateLimiter - failAllQueuedIfError=true - with errors") {

    val rateLimiter: FutureRateLimiter = new FutureRateLimiterImpl(
      system,
      "rl" + nextNameCount.incrementAndGet(),
      FutureRateLimiterConfig(
        2,
        failAllQueuedIfError = true,
        totalTimeoutForSingleFuture = 30.seconds,
        pause = 0.seconds
      )
    )

    val r1 = rateLimiter.execute { futureFunc(true) }
    val r2 = rateLimiter.execute { Future {
      Thread.sleep(400)
      throw MyFutureError()
    } }
    val r3 = rateLimiter.execute { Future.never }
    val r4 = rateLimiter.execute { Future.never }
    val r5 = rateLimiter.execute { Future.never }

    assert(await(r1) == true)
    intercept[MyFutureError](await(r2))
    intercept[FutureRateLimiterException](await(r3))
    intercept[FutureRateLimiterException](await(r4))
    intercept[FutureRateLimiterException](await(r5))

  }

  test("rateLimiter - totalTimeoutForSingleFuture") {

    val rateLimiter: FutureRateLimiter = new FutureRateLimiterImpl(
      system,
      "rl" + nextNameCount.incrementAndGet(),
      FutureRateLimiterConfig(
        2,
        failAllQueuedIfError = false,
        totalTimeoutForSingleFuture = 500.milliseconds,
        pause = 0.seconds
      )
    )

    val r1 = rateLimiter.execute { Future.never }

    intercept[AskTimeoutException](await(r1))

  }

  test("rateLimiter - skipping pause if ViaCahce-cachehit") {

    val rateLimiter: FutureRateLimiter = new FutureRateLimiterImpl(
      system,
      "rl" + nextNameCount.incrementAndGet(),
      FutureRateLimiterConfig(
        2,
        failAllQueuedIfError = false,
        totalTimeoutForSingleFuture = 500.milliseconds,
        pause = 100.seconds
      )
    )

    case class ViaCache(value:Boolean, cachedResult:Boolean) extends MaybeCachedResult

    val r1 = rateLimiter.execute { fs(ViaCache(true, cachedResult = true)) }
    val r2 = rateLimiter.execute { fs(ViaCache(true, cachedResult = false)) }

    assert(await(r1) == ViaCache(true, cachedResult = true))
    intercept[AskTimeoutException](await(r2))

  }

  test("rateLimiter - reconfigure") {

    val rateLimiter: FutureRateLimiter = new FutureRateLimiterImpl(
      system,
      "rl" + nextNameCount.incrementAndGet(),
      FutureRateLimiterConfig(
        2,
        failAllQueuedIfError = true,
        totalTimeoutForSingleFuture = 30.seconds,
        pause = 0.seconds
      )
    )

    val r1 = rateLimiter.execute { futureFunc(true) }
    val r2 = rateLimiter.execute { futureFunc(false) }
    val r3 = rateLimiter.execute { Future.never }
    val r4 = rateLimiter.execute { Future.never }
    val r5 = rateLimiter.execute { Future.never }

    assert(await(r1) == true)
    intercept[MyFutureError](await(r2))
    intercept[FutureRateLimiterException](await(r3))
    intercept[FutureRateLimiterException](await(r4))
    intercept[FutureRateLimiterException](await(r5))

    // Reconfigure it
    val newConfig = FutureRateLimiterConfig(
      2,
      failAllQueuedIfError = false,
      totalTimeoutForSingleFuture = 30.seconds,
      pause = 0.seconds
    )

    rateLimiter.stopCurrentWork()
    rateLimiter.reconfigure(newConfig)

    val ar1 = rateLimiter.execute { futureFunc(true) }
    val ar2 = rateLimiter.execute { futureFunc(false) }
    val ar3 = rateLimiter.execute { futureFunc(true) }
    val ar4 = rateLimiter.execute { futureFunc(true) }
    val ar5 = rateLimiter.execute { futureFunc(true) }

    assert(await(ar1) == true)
    intercept[MyFutureError](await(ar2))
    assert(await(ar3) == true)
    assert(await(ar4) == true)
    assert(await(ar5) == true)

  }


  case class MyFutureError() extends Exception

  def futureFunc(shouldWork: Boolean, cachedResult:Boolean = false): Future[Boolean] = {
    if (shouldWork) {
      fs(true)
    } else {
      fe(MyFutureError())
    }
  }

}
