package org.jetbrains.plugins.scala
package compiler

import java.net.{ConnectException, InetAddress, UnknownHostException}

import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.RemoteResourceOwner

/**
 * User: Dmitry Naydanov
 * Date: 2/24/14
 */
class RemoteServerRunner(project: Project) extends RemoteResourceOwner {
  protected val address = InetAddress.getByName(null)

  protected val port = ScalaApplicationSettings.getInstance().COMPILE_SERVER_PORT

  def buildProcess(arguments: Seq[String], client: Client) = new CompilationProcess {
    val COUNT = 10

    var callbacks: Seq[() => Unit] = Seq.empty

    override def addTerminationCallback(callback: => Unit) {
      this.callbacks = this.callbacks :+ (() => callback)
    }

    override def run() {
      try {
        for (i <- 0 until (COUNT - 1)) {
          try {
            Thread.sleep(i*20)
            send(serverAlias, arguments, client)
            return
          } catch {
            case _: ConnectException => Thread.sleep(100)
          }
        }

        send(serverAlias, arguments, client)
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

class RemoteServerStopper(val port: Int) extends RemoteResourceOwner {
  override protected val address: InetAddress = InetAddress.getByName(null)

  def sendStop(): Unit =
    try {
      val stopCommand = "stop_" + ScalaApplicationSettings.getInstance().COMPILE_SERVER_ID
      send(stopCommand, Seq(s"--nailgun-port $port"), null)
    } catch {
      case e: Exception =>
    }
}
