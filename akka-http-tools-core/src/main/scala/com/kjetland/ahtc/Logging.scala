package com.kjetland.ahtc

import com.typesafe.scalalogging.Logger


trait Logging {

  val log:Logger = Logger(getClass)
}
