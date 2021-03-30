package org.jetbrains.plugins.scala
package compiler

import java.net.{ConnectException, InetAddress, UnknownHostException}
import java.nio.file.Path

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.{CompileServerCommand, RemoteResourceOwner}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.RemoteServerRunner._
import org.jetbrains.plugins.scala.server.CompileServerToken

import scala.util.control.NonFatal

/**
 * @see [[NonServerRunner]]
 */
class RemoteServerRunner(project: Project)
  extends RemoteResourceOwner {

  override protected val address: InetAddress = InetAddress.getByName(null)

  override protected val port: Int = ScalaCompileServerSettings.getInstance().COMPILE_SERVER_PORT

  def buildProcess(command: CompileServerCommand, client: Client): CompilationProcess =
    buildProcess(command.id, command.asArgs, client)

  // TODO: make it cancelable, if request is hanging we cant cancel it now.
  //  E.g. when the server is down and we retry to connect to it.
  // TODO: naming is a bit scaring, it suggests that it returns some new OS Process which connects to the server
  def buildProcess(command: String,
                   arguments: Seq[String],
                   client: Client): CompilationProcess = new CompilationProcess {
    val COUNT = 10

    var callbacks: Seq[Option[Throwable] => Unit] = Seq.empty

    override def addTerminationCallback(callback: Option[Throwable] => Unit): Unit =
      this.callbacks :+= callback

    override def run(): Unit = {
      val buildSystemDir = BuildManager.getInstance.getBuildSystemDirectory(project)
      var unhandledException: Option[Throwable] = None
      try {
        for (i <- 0 until (COUNT - 1)) {
          try {
            Thread.sleep(i*20)
            val token = readToken(buildSystemDir, port)
            send(command, token +: arguments, client)
            return
          } catch {
            case _: ConnectException | _: CantFindSecureTokenException => Thread.sleep(100)
          }
        }

        val token = readToken(buildSystemDir, port)
        send(command, token +: arguments, client)
      } catch {
        case e: ConnectException =>
          val message = ScalaBundle.message("cannot.connect.to.compile.server", address.toString, port)
          client.error(message)
          reportException(message, e, client)
        case e: UnknownHostException =>
          val message = unknownHostErrorMessage
          client.error(message)
          reportException(message, e, client)
        case NonFatal(ex) =>
          unhandledException = Some(ex)
      } finally {
        callbacks.foreach(_.apply(unhandledException))
      }
    }

    override def stop(): Unit = {
      // TODO: SCL-17265 do not stop the whole server! Investigate whether we can cancel
      CompileServerLauncher.ensureServerNotRunning(project)
    }
  }
}

private object RemoteServerRunner {
  private class CantFindSecureTokenException extends Exception

  private def readToken(buildSystemDir: Path, port: Int): String =
    CompileServerToken.tokenForPort(buildSystemDir, port).getOrElse(throw new CantFindSecureTokenException)
}
