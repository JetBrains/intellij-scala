package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.RemoteResourceOwner
import org.jetbrains.plugins.scala.compiler.RemoteServerRunner._
import org.jetbrains.plugins.scala.server.{CompileServerPort, CompileServerToken}

import java.net.{ConnectException, InetAddress, UnknownHostException}
import java.nio.file.Path
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.control.NonFatal

/**
 * @see `org.jetbrains.plugins.scala.worksheet.server.NonServerRunner`
 */
final class RemoteServerRunner(project: Project) extends RemoteResourceOwner {

  override protected val address: InetAddress = InetAddress.getByName(null)

  override protected def compileServerPort: CompileServerPort = CompileServerLauncher.compileServerPort.getOrElse(throw new TCPPortMissingException())

  override protected val socketConnectTimeout: FiniteDuration =
    RegistryManager.getInstance().intValue("scala.compile.server.socket.connect.timeout.milliseconds").milliseconds

  // TODO: make it cancelable, if request is hanging we cant cancel it now.
  //  E.g. when the server is down and we retry to connect to it.
  // TODO: naming is a bit scaring, it suggests that it returns some new OS Process which connects to the server
  def buildProcess(command: String,
                   arguments: Seq[String],
                   client: Client): CompilationProcess = new CompilationProcess {
    val ConnectionRetryAttempts = 10

    var callbacks: Seq[Option[Throwable] => Unit] = Seq.empty

    override def addTerminationCallback(callback: Option[Throwable] => Unit): Unit =
      this.callbacks :+= callback

    override def run(): Unit = {
      val scalaCompileServerSystemDir = CompileServerLauncher.scalaCompileServerSystemDir(project)
      var unhandledException: Option[Throwable] = None
      try {
        for (i <- 0 until ConnectionRetryAttempts - 1) {
          try {
            Thread.sleep(i * 20)
            val token = readToken(scalaCompileServerSystemDir, compileServerPort.forToken)
            send(command, token +: arguments, client)
            return
          } catch {
            case _: ConnectException | _: TCPPortMissingException | _: CantFindSecureTokenException =>
              Thread.sleep(100)
          }
        }

        val token = readToken(scalaCompileServerSystemDir, compileServerPort.forToken)
        send(command, token +: arguments, client)
      } catch {
        case e: ConnectException =>
          val message = ScalaCompileServerMessages.cantConnectToCompileServerErrorMessage(address, compileServerPort.forCommunication)
          client.error(message)
          Log.error(message, e)

        case e: UnknownHostException =>
          val message = ScalaCompileServerMessages.unknownHostErrorMessage(address)
          client.error(message)
          Log.error(message, e)

        case NonFatal(ex) =>
          unhandledException = Some(ex)
      } finally {
        callbacks.foreach(_.apply(unhandledException))
      }
    }

    override def stop(): Unit = {
      // TODO: SCL-17265 do not stop the whole server!
      // Investigate whether we can cancel single NGSession thread to stop worksheet execution
      CompileServerLauncher.stopServerAndWaitFor(Duration.Zero)
    }
  }
}

private object RemoteServerRunner {
  private class CantFindSecureTokenException extends Exception

  private val Log = Logger.getInstance(classOf[RemoteServerRunner])

  private def readToken(scalaCompileServerSystemDir: Path, port: Int): String =
    CompileServerToken.tokenForPort(scalaCompileServerSystemDir, port).getOrElse(throw new CantFindSecureTokenException)

  private final class TCPPortMissingException extends Exception("Cannot connect to the Scala Compile Server: unknown TCP port")
}
