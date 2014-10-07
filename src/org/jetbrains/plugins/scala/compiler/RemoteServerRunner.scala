package org.jetbrains.plugins.scala
package compiler

import java.net.{ConnectException, InetAddress, UnknownHostException}

import com.intellij.openapi.project.Project
import com.intellij.util.Base64Converter
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.RemoteResourceOwner

/**
 * User: Dmitry Naydanov
 * Date: 2/24/14
 */
class RemoteServerRunner(project: Project) extends RemoteResourceOwner {
  protected val address = InetAddress.getByName(null)

  protected val port =
    try
      Integer parseInt ScalaApplicationSettings.getInstance().COMPILE_SERVER_PORT
    catch {
      case e: NumberFormatException =>
        throw new IllegalArgumentException("Bad port: " + ScalaApplicationSettings.getInstance().COMPILE_SERVER_PORT , e)
    }
  
  def run(arguments: Seq[String], client: Client) = new CompilationProcess {
    val COUNT = 10

    var callbacks: Seq[() => Unit] = Seq.empty

    override def addTerminationCallback(callback: => Unit) {
      this.callbacks = this.callbacks :+ (() => callback)
    }

    override def run() {
      val encodedArgs = arguments map (s => Base64Converter.encode(s getBytes "UTF-8"))

      try {
        for (i <- 0 until (COUNT - 1)) {
          try {
            Thread.sleep(i*20)
            send(serverAlias, encodedArgs, client)
            return
          } catch {
            case _: ConnectException => Thread.sleep(100)
          }
        }

        send(serverAlias, encodedArgs, client)
      } catch {
        case e: ConnectException =>
          val message = "Cannot connect to compile server at %s:%s".format(address.toString, port)
          client.error(message)
        case e: UnknownHostException =>
          val message = "Unknown IP address of compile server host: " + address.toString
          client.error(message)
      } finally {
        callbacks.foreach(a => a())
      }
    }

    override def stop() {
      CompileServerLauncher.ensureNotRunning(project)
    }
  }
}
