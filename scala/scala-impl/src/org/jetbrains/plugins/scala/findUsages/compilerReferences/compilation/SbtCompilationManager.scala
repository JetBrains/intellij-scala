package org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation

import java.io._
import java.net.{ServerSocket, Socket}
import java.nio.file.Paths
import java.util.UUID

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.SbtCompilationListener.ProjectIdentifier
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.SbtCompilationListener.ProjectIdentifier._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSbtSettings
import org.jetbrains.plugins.scala.indices.protocol.sbt._

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
class SbtCompilationManager() extends BaseComponent {
  import SbtCompilationManager._

  private[this] var server: ServerSocket = _
  private[this] val bus: MessageBus      = ApplicationManager.getApplication.getMessageBus
  private[this] val port                 = CompilerIndicesSbtSettings().sbtConnectionPort

  Disposer.register(ApplicationManager.getApplication, () => if (server != null) server.close())

  override def initComponent(): Unit =
    try {
      server = new ServerSocket(port)
      logger.debug(s"Listening to incoming sbt compilation events on port $port.")
      executeOnPooledThread {
        while (true) {
          try {
            val client = server.accept()
            executeOnPooledThread(handleConnection(client))
          } catch {
            case e: IOException =>
              logger.error(e)
              onConnectionFailure(Unidentified, None)
          }
        }
      }
    } catch {
      case NonFatal(e) =>
        logger.error(s"Failed to open a socket to listen for SBT compilations on port: $port.", e)
        if (server != null) server.close()
    }

  private[this] def handleConnection(client: Socket): Unit = {
    var base: ProjectIdentifier     = Unidentified
    var id: Option[UUID]            = None

    try {
      val in            = new DataInputStream(client.getInputStream())
      val projectBase   = ProjectBase(Paths.get(in.readUTF()))
      val compilationId = UUID.fromString(in.readUTF())
      base = projectBase
      id   = Option(compilationId)

      try bus.syncPublisher(SbtCompilationListener.topic).beforeCompilationStart(projectBase, compilationId)
      catch { case NonFatal(e) => logger.error(e) }

      val out = new DataOutputStream(client.getOutputStream())
      out.writeUTF(ideaACK)

      val isSuccessful = in.readBoolean()
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
        logger.error(e)
        onConnectionFailure(base, id)
    } finally if (client != null) client.close()
  }

  private[this] def onConnectionFailure(identifier: ProjectIdentifier, compilationId: Option[UUID]): Unit =
    try bus.syncPublisher(SbtCompilationListener.topic).connectionFailure(identifier, compilationId)
    catch { case NonFatal(e) => logger.error(e) }
}

object SbtCompilationManager {
  private val logger = Logger.getInstance(classOf[SbtCompilationManager])
}
