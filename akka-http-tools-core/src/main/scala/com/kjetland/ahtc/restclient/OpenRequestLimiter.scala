package com.kjetland.ahtc.restclient

import akka.actor.ActorSystem
import com.kjetland.ahtc.calllimiter.CallLimiterPercentageImpl

import scala.concurrent.ExecutionContext

// This is a workaround for https://github.com/akka/akka-http/issues/1186
class OpenRequestLimiter
(
  system:ActorSystem,
  ec: ExecutionContext,
  subName: String
) extends CallLimiterPercentageImpl(
  ec,
  "OpenRequestLimiter",
  subName,
  maxTotalRequests = system.settings.config.getInt("akka.http.host-connection-pool.max-open-requests"),
  99
) {



}
