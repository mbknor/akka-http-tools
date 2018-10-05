package com.kjetland.ahtc.restserver

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.kjetland.ahtc.JacksonJsonSupport
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

trait RestServerHelper extends JacksonJsonSupport with ExceptionToHttpError {

  protected val route:Route

  // Workaround for https://github.com/lomigmegard/akka-http-cors/issues/37
  def corsSettings(actorSystem:ActorSystem):CorsSettings = CorsSettings(actorSystem)

  def getRoute( actorSystem:ActorSystem ):Route = {
    handleRejections(corsRejectionHandler) {
      cors(corsSettings(actorSystem)) {
        handleRejections(RejectionHandler.default) {
          handleExceptions(myExceptionHandler) {
            rejectEmptyResponse {
              route
            }
          }
        }
      }
    }
  }


}
