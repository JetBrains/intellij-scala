package org.jetbrains.plugins.scala.compiler.references.compilation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.scala.components.RunOnceStartupActivity
import org.jetbrains.plugins.scala.compiler.references.compilation
import org.jetbrains.plugins.scala.compiler.references.compilation.SbtCompilationListener.ProjectIdentifier
import org.jetbrains.plugins.scala.compiler.references.compilation.SbtCompilationListener.ProjectIdentifier._
import org.jetbrains.plugins.scala.indices.protocol.sbt._
import org.jetbrains.plugins.scala.settings.CompilerIndicesSbtSettings
import org.jetbrains.sbt.SbtCompilationSupervisorPort

import java.io._
import java.net.{ServerSocket, Socket}
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.{ExecutorService, Executors, Future => JFuture}
import scala.util.control.NonFatal

/**
 * Listens to incoming SBT connections and sends compilation notifications via
 * [[compilation.SbtCompilationListener.topic]].
  *
  * The protocol is as follows:
  * - sbt notifies IDEA that compilation is about to start and awaits response
  * - IDEA executes `beforeCompilationStart` callbacks and sends `ack` to sbt
  * - compilation proceeds
  * - sbt notifies IDEA that compilation has completed/failed and awaits response
  * - IDEA executes `onCompilationSuccess/onCompilationFailure` callbacks and sends `ack` to sbt
  * (in case on any connection related failures `onConnectionFailure` callbacks are executed).
  *
  * Explicit waiting for acknowledgement on the sbt side is done in order to ensure that
  * dependent compilations are processed in their respective order.
 */
class SbtCompilationSupervisor {
  import SbtCompilationSupervisor._

  private[this] val executor: ExecutorService = Executors.newCachedThreadPool()
  private[this] var server: ServerSocket      = _
  private[this] val bus: MessageBus           = ApplicationManager.getApplication.getMessageBus
  private[this] val port                      = CompilerIndicesSbtSettings().sbtPort

  private[this] def executeOnPooledThread[T](body: =>T): JFuture[T] =
    executor.submit[T](() => body)

  private def init(): Unit =
    try {
      server = new ServerSocket(port)
      SbtCompilationSupervisorPort.port = server.getLocalPort
      logger.info(s"Listening to incoming sbt compilation events on port $port.")
      executeOnPooledThread {
        withCustomThreadName {
          while (server != null && !server.isClosed) {
            try {
              val client = server.accept()
              executeOnPooledThread(handleConnection(client))
            } catch {
              case e: IOException =>
                if (!server.isClosed) {
                  logger.error(e)
                  onConnectionFailure(Unidentified, None)
                }
            }
          }
        }
      }
    } catch {
      case NonFatal(e) =>
        logger.error(s"Failed to open a socket to listen for SBT compilations on port: $port.", e)
        if (server != null) server.close()
    }

  private def dispose(): Unit = {
    executor.shutdown()
    if (server != null)
      server.close()
  }

  private[this] def handleConnection(client: Socket): Unit = {
    var base: ProjectIdentifier = Unidentified
    var id: Option[UUID]        = None

    try {
      val in            = new DataInputStream(client.getInputStream)
      val projectBase   = ProjectBase(Paths.get(in.readUTF()))
      val compilationId = UUID.fromString(in.readUTF())
      base = projectBase
      id   = Option(compilationId)

      try bus.syncPublisher(SbtCompilationListener.topic).beforeCompilationStart(projectBase, compilationId)
      catch { case NonFatal(e) => logger.error(e) }

      val out = new DataOutputStream(client.getOutputStream)
      logger.info(s"sbt compilation started id: $compilationId, project base: $base")
      out.writeUTF(ideaACK)

      val isSuccessful = in.readBoolean()
      logger.info(s"sbt compilation finished id: $compilationId, success: $isSuccessful")
      val publisher    = bus.syncPublisher(SbtCompilationListener.topic)

      if (isSuccessful) {
        val infoFile = in.readUTF()

        try publisher.onCompilationSuccess(projectBase, compilationId, infoFile)
        catch { case NonFatal(e) => logger.error(e) }
      } else
        try publisher.onCompilationFailure(projectBase, compilationId)
        catch { case NonFatal(e) => logger.error(e) }

      out.writeUTF(ideaACK)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        logger.info(e)
        onConnectionFailure(base, id)
    } finally if (client != null) client.close()
  }

  private[this] def onConnectionFailure(identifier: ProjectIdentifier, compilationId: Option[UUID]): Unit =
    try bus.syncPublisher(SbtCompilationListener.topic).connectionFailure(identifier, compilationId)
    catch { case NonFatal(e) => logger.error(e) }
}

object SbtCompilationSupervisor {
  private val connectionThreadName = "SbtCompilationSupervisor server connection thread"

  private def withCustomThreadName(action: => Unit): Unit = {
    val thread = Thread.currentThread()
    val oldName = thread.getName
    thread.setName(connectionThreadName)
    try
      action
    finally
      thread.setName(oldName)
  }

  private val logger = Logger.getInstance(classOf[SbtCompilationSupervisor])

  def apply(): SbtCompilationSupervisor =
    ApplicationManager.getApplication.getService(classOf[SbtCompilationSupervisor])

  private class Activity extends RunOnceStartupActivity {

    override def doRunActivity(): Unit = {
      SbtCompilationSupervisor().init()
    }

    override def doCleanup(): Unit = {
      SbtCompilationSupervisor().dispose()
    }
  }
}
