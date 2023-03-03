package org.jetbrains.bsp

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, DefaultActionGroup, Separator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget}
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import com.intellij.util.messages.MessageBusConnection
import org.apache.commons.io.IOUtils
import org.jetbrains.bsp.protocol.{BspCommunication, BspCommunicationService}
import org.jetbrains.bsp.settings.BspProjectSettings.BspServerConfig

import java.awt.Point
import java.awt.event.MouseEvent
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

@nowarn("msg=trait Consumer in package util is deprecated") //We have to use deprecated consumer because it's still used in upstream API
private final class BspServerWidget extends StatusBarWidget
  with StatusBarWidget.IconPresentation
  with Consumer[MouseEvent]
  with BspServerWidgetProvider.UpdateWidgetListener {

  private val connection: MessageBusConnection = ApplicationManager.getApplication.getMessageBus.connect()
  private var statusBar: StatusBar = _

  override def ID(): String = BspServerWidgetProvider.ID

  override def install(statusBar: StatusBar): Unit = {
    this.statusBar = statusBar
    connection.subscribe(BspServerWidgetProvider.Topic, this)
  }

  override def dispose(): Unit = {
    connection.dispose()
  }
  
  override def getPresentation: StatusBarWidget.WidgetPresentation = this
  
  override def getIcon: Icon =
    if (connectionsActive) BspServerWidgetProvider.IconRunning
    else BspServerWidgetProvider.IconStopped

  override def getTooltipText: String = BspBundle.message("bsp.widget.bsp.connection")

  override def getClickConsumer: Consumer[MouseEvent] = this
  
  override def consume(e: MouseEvent): Unit = {
    val openCom = BspCommunicationService.getInstance.listOpenComms
    val connectionClosers =
      (openCom.map(c => new CloseBspSession(c._1, c._2)).toList
        ++ List(new CloseAllSessions)
        ++ openCom.map(c => new TerminateServer(c._1, c._2)).toList
        :+ Separator.getInstance
        ).asJava

    val group = new DefaultActionGroup(connectionClosers)
    val mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS
    val context = DataManager.getInstance.getDataContext(e.getComponent)
    val title = BspBundle.message("bsp.widget.connections", if (connectionsActive) BspBundle.message("bsp.widget.connections.on") else BspBundle.message("bsp.widget.connections.off"))

    val popup = JBPopupFactory.getInstance.createActionGroupPopup(title, group, context, mnemonics, true)
    val dimension = popup.getContent.getPreferredSize
    val at = new Point(0, -dimension.height)
    popup.show(new RelativePoint(e.getComponent, at))
  }

  override def updateWidget(): Unit = {
    if (statusBar ne null) {
      statusBar.updateWidget(ID())
    }
  }

  private def connectionsActive: Boolean = {
    val comService = BspCommunicationService.getInstance
    comService.listOpenComms.exists((comService.isAlive _).tupled)
  }

  //noinspection ReferencePassedToNls
  private final class CloseBspSession(uri: URI, config: BspServerConfig) extends AnAction(uri.toString, BspBundle.message("bsp.widget.kill.bsp.connection.at.uri", uri), AllIcons.Actions.Suspend) with DumbAware {
    override def update(e: AnActionEvent): Unit = {
      val isAlive = BspCommunicationService.getInstance.isAlive(uri, config)
      e.getPresentation.setEnabled(isAlive)
    }

    override def actionPerformed(e: AnActionEvent): Unit = {
      BspCommunicationService.getInstance.closeCommunication(uri, config)
    }
  }
  
  private final class CloseAllSessions extends AnAction(BspBundle.message("bsp.widget.stop.all.bsp.connections"), BspBundle.message("bsp.widget.stop.all.bsp.connections"), AllIcons.Actions.Suspend) with DumbAware {

    override def update(e: AnActionEvent): Unit = {
      e.getPresentation.setEnabled(connectionsActive)
    }

    override def actionPerformed(e: AnActionEvent): Unit = {
      BspCommunicationService.getInstance.closeAll
    }
  }

  private final class TerminateServer(uri: URI, config: BspServerConfig) extends AnAction(BspBundle.message("bsp.widget.bsp.terminate.server", uri), BspBundle.message("bsp.widget.bsp.terminate.server", uri), AllIcons.Actions.Exit) with DumbAware {
    override def update(e: AnActionEvent): Unit = {
      val exitCommandDefined = BspCommunicationService.getInstance.exitCommands(uri, config).toOption.exists(_.nonEmpty)
      e.getPresentation.setVisible(exitCommandDefined)
    }

    override def actionPerformed(e: AnActionEvent): Unit = try {
      val commands = BspCommunicationService.getInstance.exitCommands(uri, config).getOrElse(List())
      BspCommunicationService.getInstance.closeAll
      ApplicationManager.getApplication.executeOnPooledThread(
        new Runnable {
          override def run(): Unit = {
            if (commands.isEmpty) {
              BspServerWidgetProvider.logger.info(s"BSP server exit command not set in '${BspCommunication.argvExit}' field")
            } else {
              BspServerWidgetProvider.logger.info(s"Running BSP server restart")
            }
            val timeoutLength = 30
            commands.foreach { command =>
              BspServerWidgetProvider.logger.info(s"Running comand: '${command.mkString(" ")}'")
              val process = new ProcessBuilder(command.asJava).start()
              val timedOut = !process.waitFor(timeoutLength, TimeUnit.SECONDS)
              if (timedOut) {
                // We log only timeouts, and skip regular errors because they occur too often,
                // ex. when the server is just not running.
                val title = s"Timeout when terminating BSP server after ${timeoutLength} seconds"
                val stderr = s"[STDERR]\n${IOUtils.toString(process.getErrorStream, "UTF-8")}\n"
                val stdout = s"[STDOUT]\n${IOUtils.toString(process.getInputStream, "UTF-8")}\n"
                BspServerWidgetProvider.logger.error(s"$title\n $stderr\n $stdout")
              }
            }
          }
        }
      )
    } catch {
      case e: Throwable => BspServerWidgetProvider.logger.error(e)
    }
  }
}
