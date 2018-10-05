package com.kjetland.ahtc

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentType, HttpEntity}
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import scala.concurrent.duration._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait JacksonJsonSupport {
  val objectMapper:ObjectMapper

  // Override this to produce custom version
  val marshallAsContentType:ContentType = `application/json`.withParams(Map("charset" -> "utf-8"))

  implicit def JacksonMarshaller: ToEntityMarshaller[AnyRef] = {
    Marshaller.withFixedContentType(marshallAsContentType) { obj =>
      HttpEntity(marshallAsContentType, objectMapper.writeValueAsString(obj).getBytes("UTF-8"))
    }
  }

  implicit def JacksonUnmarshaller[T <: AnyRef](implicit c: ClassTag[T]): FromEntityUnmarshaller[T] = {
    new FromEntityUnmarshaller[T] {
      override def apply(entity: HttpEntity)(implicit ec: ExecutionContext, materializer: Materializer): Future[T] = {
        entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8")).map { str =>
          objectMapper.readValue(str, c.runtimeClass).asInstanceOf[T]
        }
      }
    }
  }


  def fromJsonList[T:ClassTag](json:String):List[T] = {
    import scala.collection.JavaConverters._

    def ctag = implicitly[reflect.ClassTag[T]]
    val clazz = ctag.runtimeClass.asInstanceOf[Class[T]]
    val tree = objectMapper.readTree(json).asInstanceOf[ArrayNode]

    var list = List[T]()
    tree.elements().asScala.foreach { e =>
      list = list :+ objectMapper.treeToValue(e, clazz)
    }

    list
  }
}

