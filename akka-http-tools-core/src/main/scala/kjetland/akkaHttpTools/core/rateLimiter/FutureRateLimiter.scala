package kjetland.akkaHttpTools.core.rateLimiter

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import kjetland.akkaHttpTools.core.Logging

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


trait MaybeCachedResult {
  def cachedResult:Boolean
}

// Limits the number of concurrent "running" futures
// Running means started but not completed
trait FutureRateLimiter {

  def apply[T:ClassTag](f: => Future[T]):Future[T] = {
    execute(f)
  }

  def execute[T:ClassTag](f: => Future[T]):Future[T]

  def stopCurrentWork():Unit

  def reconfigure(newConfig:FutureRateLimiterConfig)

}

object FutureRateLimiterConfig {

  def default:FutureRateLimiterConfig = FutureRateLimiterConfig(
    1,
    failAllQueuedIfError = true,
    FiniteDuration(4, TimeUnit.SECONDS),
    FiniteDuration(0, TimeUnit.SECONDS)
  )
}

case class FutureRateLimiterConfig
(
  maxRunningFutures:Int,
  failAllQueuedIfError:Boolean, // Will fail all until rateLimiter is reconfigured
  totalTimeoutForSingleFuture:FiniteDuration, // the time from which we submit it for later execution until it completes
  pause:FiniteDuration
)

object FutureRateLimiterImpl {

  val nextActorNameInt = new AtomicInteger(0)
}

class FutureRateLimiterImpl
(
  system:ActorSystem,
  name:String,
  initialConfig:FutureRateLimiterConfig
) extends FutureRateLimiter {
  import FutureRateLimiterImpl._
  import FutureRateLimiterActor._
  import akka.pattern.ask

  var config = initialConfig

  val nameToUse = s"rate-limiter-${name}"

  val rateLimiterActor:ActorRef = system.actorOf( FutureRateLimiterActor.props(
    nameToUse,
    config), nameToUse+"-" + nextActorNameInt.incrementAndGet() )

  override def execute[T:ClassTag](f: => Future[T]): Future[T] = {
    ask( rateLimiterActor, NewJobMsg( {() => f }))(timeout = config.totalTimeoutForSingleFuture).mapTo[T]
  }


  override def stopCurrentWork(): Unit = {
    rateLimiterActor ! StopCurrentWorkMsg()
  }

  override def reconfigure(newConfig: FutureRateLimiterConfig): Unit = {
    // Setting local config
    config = newConfig
    // Sending config to actor
    rateLimiterActor ! newConfig
  }
}

case class FutureRateLimiterException(msg:String) extends Exception(msg)

object FutureRateLimiterActor {
  case class NewJobMsg(f: () => Future[_])
  case class Job(jobNo:Int, f: () => Future[_], sender:ActorRef)
  case class JobCompletedMsg(jobNo:Int, result:Any, sender:ActorRef)
  case class JobFailedMsg(jobNo:Int, exception:Throwable, sender:ActorRef)
  case class StopCurrentWorkMsg()


  def props
  (
    name:String,
    config:FutureRateLimiterConfig
  ):Props = {
    Props( new FutureRateLimiterActor(name, config))
  }
}

class FutureRateLimiterActor
(
  name:String,
  initialConfig:FutureRateLimiterConfig
) extends Actor with Logging {
  import FutureRateLimiterActor._
  import context._

  // Can be reconfigured
  var config:FutureRateLimiterConfig = initialConfig

  var notStartedJobs:List[Job] = List()

  var runningJobs:Set[ActorRef] = Set()

  val nextJobNo = new AtomicInteger(0)

  var totalJobCount:Int = 0
  var cacheHits:Int = 0

  def receive = {
    case NewJobMsg(f) => addNewJob( Job(nextJobNo.getAndIncrement(), f, sender) )
    case result:JobCompletedMsg => jobCompleted(result)
    case result:JobFailedMsg => jobFailed(result)
    case StopCurrentWorkMsg() => failAllJobsLeft()
    case newConfig:FutureRateLimiterConfig => reconfigure(newConfig)
  }

  def closed:Receive = {
    case NewJobMsg(f) => notAcceptingNewJob()
    case newConfig:FutureRateLimiterConfig => reconfigure(newConfig)
  }

  def notAcceptingNewJob(): Unit = {
    val msg = s"Rejecting new job due to failAllQueuedIfError=true"
    log.debug(s"$name - $msg")

    val error = FutureRateLimiterException(msg)
    sender ! akka.actor.Status.Failure(error)
  }

  def addNewJob(job:Job): Unit = {
    log.debug(s"$name - Adding new job")

    totalJobCount = totalJobCount + 1

    notStartedJobs = notStartedJobs :+ job
    checkAndStartNewJob()
  }

  def checkAndStartNewJob(): Unit = {
    if ( runningJobs.size < config.maxRunningFutures ) {
      notStartedJobs.headOption.foreach { jobToStart =>
        notStartedJobs = notStartedJobs.tail
        startJob( jobToStart )
      }
    }
  }

  def startJob(job:Job): Unit = {
    runningJobs = runningJobs + job.sender
    log.debug(s"$name - Starting job #${job.jobNo} (runningJobs: $runningJobs)")

    job.f.apply().onComplete {
      case Success(result)    =>


        val cachedResult:Boolean = result match {
          case x:MaybeCachedResult => x.cachedResult
          case _ => false
        }

        sendResultToSelf( JobCompletedMsg(job.jobNo, result, job.sender), cachedResult )

      case Failure(exception) =>
        sendResultToSelf( JobFailedMsg(job.jobNo, exception, job.sender), cacheHit = false )
    }
  }

  def sendResultToSelf(msg:Any, cacheHit:Boolean): Unit = {

    if ( cacheHit ) {
      cacheHits = cacheHits + 1
      self ! msg
    } else if ( config.pause.toMillis == 0 ) {
      self ! msg
    } else {
      context.system.scheduler.scheduleOnce(config.pause, self, msg)
    }
  }

  def jobCompleted(jobCompleted:JobCompletedMsg): Unit = {
    log.debug(s"$name - Job #${jobCompleted.jobNo} completed")
    runningJobs = runningJobs - jobCompleted.sender

    // Send result to sender
    jobCompleted.sender ! jobCompleted.result

    checkAndStartNewJob()
  }

  def jobFailed(jobFailed:JobFailedMsg): Unit = {
    log.debug(s"$name - Job #${jobFailed.jobNo} failed")
    runningJobs = runningJobs - jobFailed.sender

    // Send result to sender
    jobFailed.sender ! akka.actor.Status.Failure( jobFailed.exception ) // So that ask throws exception

    if ( config.failAllQueuedIfError ) {
      failAllJobsLeft()
    } else {
      checkAndStartNewJob()
    }
  }

  def failAllJobsLeft(): Unit = {
    // Failing all jobs not yet started and any currently running..

    log.debug(s"failAllJobsLeft and become closed - totalWork: ${totalJobCount - cacheHits} (cacheHits: $cacheHits) - will open after reconfig")

    if ( notStartedJobs.nonEmpty ) {

      log.debug(s"$name - Failing all ${notStartedJobs.size} notStartedJobs")

      val error = FutureRateLimiterException("Failed job before it was started since failAllQueuedIfError=true")
      notStartedJobs.foreach { job =>
        job.sender ! akka.actor.Status.Failure(error) // so that ask throws exception
      }
    }

    if ( runningJobs.nonEmpty ) {
      log.debug(s"$name - Failing all ${runningJobs.size} runningJobs")

      val error = FutureRateLimiterException("Failed job after it was started since failAllQueuedIfError=true")
      runningJobs.foreach { _sender =>
        _sender ! akka.actor.Status.Failure(error) // so that ask throws exception
      }
    }

    notStartedJobs = List()
    runningJobs = Set()

    become(closed, discardOld = true)
  }

  def reconfigure(newConfig:FutureRateLimiterConfig): Unit = {
    log.debug(s"Reconfigure to $newConfig")
    failAllJobsLeft()

    totalJobCount = 0
    cacheHits = 0

    config = newConfig

    // In case we where closed
    become(receive, discardOld = true)
  }

}
