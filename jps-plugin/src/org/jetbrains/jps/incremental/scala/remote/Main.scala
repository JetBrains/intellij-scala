package org.jetbrains.jps.incremental.scala
package remote

import local.LocalServer

/**
 * @author Pavel Fatin
 */
object Main {
  private val Server = new LocalServer()

  def main(args: Array[String]) {
    val client = new EventGeneratingClient(event => System.out.write(event.toBytes), System.out.checkError)

    try {
      val arguments = Arguments.from(args)
      Server.compile(arguments.sbtData, arguments.compilerData, arguments.compilationData, client)
    } catch {
      case e: Throwable => client.trace(e)
    }
  }
}
