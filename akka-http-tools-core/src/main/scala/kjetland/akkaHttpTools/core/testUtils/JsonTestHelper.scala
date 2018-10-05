package kjetland.akkaHttpTools.core.testUtils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode

import scala.reflect.ClassTag
import scala.collection.JavaConverters._

trait JsonTestHelper {

  def createMapperFactory:ObjectMapper

  lazy val objectMapper:ObjectMapper = createMapperFactory
  lazy val prettyObjectMapper = objectMapper.writerWithDefaultPrettyPrinter()

  def toJson(o:Any):String = {
    objectMapper.writeValueAsString(o)
  }

  def toJsonPretty(o:Any):String = {
    prettyObjectMapper.writeValueAsString(o)
  }


  def convertFromJson[T:ClassTag](json:String):T = {
    def ctag = implicitly[reflect.ClassTag[T]]
    objectMapper.readValue(json, ctag.runtimeClass.asInstanceOf[Class[T]])
  }

  def convertFromJsonList[T:ClassTag](json:String):List[T] = {
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
