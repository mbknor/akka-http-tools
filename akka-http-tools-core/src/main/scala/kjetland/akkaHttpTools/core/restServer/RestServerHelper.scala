package kjetland.akkaHttpTools.core.restServer

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives.{handleExceptions, handleRejections, rejectEmptyResponse}
import akka.http.scaladsl.server.{RejectionHandler, Route}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.{cors, corsRejectionHandler}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import kjetland.akkaHttpTools.core.JacksonJsonSupport

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
