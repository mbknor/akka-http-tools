package kjetland.akkaHttpTools.core

import com.typesafe.scalalogging.Logger

trait Logging {

  val log:Logger = Logger(getClass)
}
