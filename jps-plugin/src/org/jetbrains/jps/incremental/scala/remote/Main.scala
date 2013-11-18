package org.jetbrains.jps.incremental.scala
package remote

import local.LocalServer
import com.intellij.util.Base64Converter
import com.martiansoftware.nailgun.NGContext

/**
 * @author Pavel Fatin
 */
object Main {
  private val Server = new LocalServer()

  def nailMain(context: NGContext) {
    val args = context.getArgs.toSeq
    val out = context.out

    val client = {
      val eventHandler = (event: Event) => out.write(Base64Converter.encode(event.toBytes).getBytes)
      new EventGeneratingClient(eventHandler, out.checkError)
    }

    // Suppress any stdout data, interpret such data as error
    System.setOut(System.err)

    try {
      val arguments = {
        val strings = args.map(arg => new String(Base64Converter.decode(arg.getBytes), "UTF-8"))
        Arguments.from(strings)
      }
      Server.compile(arguments.sbtData, arguments.compilerData, arguments.compilationData, arguments.incrementalType, client)
    } catch {
      case e: Throwable => client.trace(e)
    }
  }
}
