package com.kjetland.ahtc.testUtils

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait AwaitHelper {

  def awaitHelperAtMost:FiniteDuration = 10.seconds

  def await[T](f:Future[T]):T = {
    Await.result(f, awaitHelperAtMost)
  }

  // Wraps with successfull future
  def fs[T](o:T):Future[T] = {
    Future.successful(o)
  }

  def fe[T](e:Exception):Future[T] = {
    Future.failed(e)
  }



}
