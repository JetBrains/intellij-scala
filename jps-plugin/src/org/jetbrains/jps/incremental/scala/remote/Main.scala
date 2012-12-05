package org.jetbrains.jps.incremental.scala
package remote

import local.LocalServer

/**
 * @author Pavel Fatin
 */
object Main {
  private val Server = new LocalServer()

  private val EventListener = (event: Event) => print(event.asString)

  def main(args: Array[String]) {
    val client = new EventGeneratingClient(EventListener)

    try {
      val arguments = Arguments.from(args)
      Server.compile(arguments.sbtData, arguments.compilerData, arguments.compilationData, client)
    } catch {
      case e: Throwable => client.trace(e)
    }
  }
}
