package kjetland.akkaHttpTools.core.testUtils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, ResponseEntity}
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait TestRestClientHelper {

  implicit val system:ActorSystem
  implicit val ec:ExecutionContext
  implicit val mat:ActorMaterializer

  def getBody(entity:ResponseEntity):Future[String] = {
    entity.toStrict(2000.millis)
      .map {
        e =>
          e.data.utf8String
      }
  }


  def getUrlBlocking(url:String):String = {
    val f = Http().singleRequest(HttpRequest(uri = url))
      .flatMap( r => getBody(r.entity))
    Await.result(f, 10.seconds)
  }

}
