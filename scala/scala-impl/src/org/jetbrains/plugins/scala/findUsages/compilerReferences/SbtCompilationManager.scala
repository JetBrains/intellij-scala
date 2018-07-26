package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io._
import java.net.{ServerSocket, Socket}

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SbtCompilationListener.ProjectIdentifier
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SbtCompilationListener.ProjectIdentifier._

import scala.util.control.NonFatal

/**
 * Listens to incoming SBT connections and sends compilation notifications via
 * [[org.jetbrains.plugins.scala.findUsages.compilerReferences.SbtCompilationListener.topic]].
 */
class SbtCompilationManager extends ApplicationComponent {
  import SbtCompilationManager._

  private[this] var server: ServerSocket = _
  private[this] val bus: MessageBus      = ApplicationManager.getApplication.getMessageBus

  override def initComponent(): Unit = {
    try {
      server = new ServerSocket(port)
      executeOnPooledThread {
        while (true) {
          try {
            val client = server.accept()
            executeOnPooledThread(handleConnection(client))
          } catch {
            case e: IOException =>
              logger.error(e)
              onConnectionFailure(Unidentified)
          }
        }
      }
    } catch {
      case NonFatal(e) =>
        logger.error(s"Failed to open a socket to listen for SBT compilations on port: $port.", e)
        if (server != null) server.close()
    }
  }

  override def disposeComponent(): Unit = if (server != null) server.close()

  private[this] def handleConnection(client: Socket): Unit = {
    var base: String = null
    try {
      val in = new DataInputStream(client.getInputStream())
      base = in.readUTF()
      val projectBase = ProjectBase(base)
      bus.syncPublisher(SbtCompilationListener.topic).beforeCompilationStart(projectBase)
      val out = new DataOutputStream(client.getOutputStream())
      out.writeUTF(ACK)

      val exitCode = in.readInt()
      bus.syncPublisher(SbtCompilationListener.topic).compilationFinished(projectBase, exitCode == 0)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        logger.error(e)
        val identifier = if (base != null) ProjectBase(base) else Unidentified
        onConnectionFailure(identifier)
    } finally if (client != null) client.close()
  }

  private[this] def onConnectionFailure(identifier: ProjectIdentifier): Unit =
    bus.syncPublisher(SbtCompilationListener.topic).connectionFailure(identifier)
}

object SbtCompilationManager {
  //@TODO: should be a setting
  private val port = 65337
  private val ACK  = "ack"

  private val logger = Logger.getInstance(classOf[SbtCompilationManager])
}
