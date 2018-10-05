package kjetland.akkaHttpTools.core.testUtils

import java.util.concurrent.ThreadLocalRandom

import com.google.common.base.Charsets
import com.google.common.io.Resources

object TestUtils {

  import java.io.IOException
  import java.net.ServerSocket

  def nextFreePort(): Int = {
    while ( true) {
      val port = ThreadLocalRandom.current.nextInt(2000, 60000)
      if (isLocalPortFree(port)) {
        return port
      }
    }
    throw new Exception("Unable to find free port")
  }

  private def isLocalPortFree(port: Int) = try {
    new ServerSocket(port).close()
    true
  } catch {
    case e: IOException =>
      false
  }

  def loadFromResources(path:String):String = {
    val r = Resources.getResource(path)
    Resources.toString(r, Charsets.UTF_8)
  }

}
