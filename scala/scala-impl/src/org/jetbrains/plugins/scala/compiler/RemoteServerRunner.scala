package org.jetbrains.plugins.scala
package compiler

import java.net.{ConnectException, InetAddress, UnknownHostException}
import java.nio.file.{Files, Paths}

import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.RemoteResourceOwner
import org.jetbrains.plugins.scala.compiler.RemoteServerRunner._

/**
 * User: Dmitry Naydanov
 * Date: 2/24/14
 */
class RemoteServerRunner(project: Project) extends RemoteResourceOwner {
  protected val address: InetAddress = InetAddress.getByName(null)

  protected val port: Int = ScalaCompileServerSettings.getInstance().COMPILE_SERVER_PORT

  def buildProcess(arguments: Seq[String], client: Client): CompilationProcess = new CompilationProcess {
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
            val token = readToken(port)
            send(serverAlias, token +: arguments, client)
            return
          } catch {
            case _: ConnectException | _: CantFindSecureTokenException => Thread.sleep(100)
          }
        }

        val token = readToken(port)
        send(serverAlias, token +: arguments, client)
      } catch {
        case e: ConnectException =>
          val message = "Cannot connect to compile server at %s:%s".format(address.toString, port)
          client.error(message)
          client.debug(s"$message\n${e.toString}\n${e.getStackTrace.mkString("\n")}")
        case e: UnknownHostException =>
          val message = "Unknown IP address of compile server host: " + address.toString
          client.error(message)
          client.debug(s"$message\n${e.toString}\n${e.getStackTrace.mkString("\n")}")
      } finally {
        callbacks.foreach(a => a())
      }
    }

    override def stop() {
      CompileServerLauncher.ensureNotRunning(project)
    }
  }
}

private object RemoteServerRunner {
  private class CantFindSecureTokenException extends Exception 
  
  private def readToken(port: Int): String = {
    val path = tokenPathFor(port)
    if (!path.toFile.exists()) throw new CantFindSecureTokenException

    new String(Files.readAllBytes(path))
  }

  private def tokenPathFor(port: Int) =
    Paths.get(System.getProperty("user.home"), ".idea-build", "tokens", port.toString)
}
