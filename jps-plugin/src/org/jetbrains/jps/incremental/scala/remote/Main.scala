package org.jetbrains.jps.incremental.scala
package remote

import local.LocalServer
import com.intellij.util.Base64Converter

/**
 * @author Pavel Fatin
 */
object Main {
  private val Server = new LocalServer()

  def main(args: Array[String]) {
    val client = {
      val eventHandler = (event: Event) => System.out.write(Base64Converter.encode(event.toBytes).getBytes)
      new EventGeneratingClient(eventHandler, System.out.checkError)
    }

    try {
      val arguments = {
        val strings = args.map(arg => new String(Base64Converter.decode(arg.getBytes), "UTF-8")).toSeq
        Arguments.from(strings)
      }
      Server.compile(arguments.sbtData, arguments.compilerData, arguments.compilationData, client)
    } catch {
      case e: Throwable => client.trace(e)
    }
  }
}
