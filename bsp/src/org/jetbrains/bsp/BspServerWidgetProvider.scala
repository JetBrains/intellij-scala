package org.jetbrains.bsp


import java.awt.Point
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import java.net.URI
import java.util.concurrent.TimeUnit

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, DefaultActionGroup, Separator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar.Anchors
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetProvider, WindowManager}
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import javax.swing.{Icon, Timer}
import org.apache.commons.io.IOUtils
import org.jetbrains.bsp.protocol.{BspCommunication, BspCommunicationService}
import org.jetbrains.bsp.settings.BspProjectSettings.BspServerConfig

import scala.jdk.CollectionConverters._

class BspServerWidgetProvider extends StatusBarWidgetProvider {

  private val IconRunning = org.jetbrains.bsp.Icons.BSP
  private val IconStopped = IconLoader.getDisabledIcon(IconRunning)
  private val logger = Logger.getInstance(classOf[BspServerWidgetProvider])
  private class Widget(project: Project) extends StatusBarWidget {

    private val timer = new Timer(1000, TimerListener)

    private def statusBar = Option(WindowManager.getInstance.getStatusBar(project))

    private object TimerListener extends ActionListener {

      override def actionPerformed(e: ActionEvent): Unit = {
        statusBar.foreach(_.updateWidget(ID))
      }
    }

    def connectionsActive: Boolean = {
      val comService = BspCommunicationService.getInstance
      comService.listOpenComms.exists((comService.isAlive _).tupled)
    }

    override def ID = "BSP Manager"

    override def getPresentation: Presentation.type = Presentation

    override def install(statusBar: StatusBar): Unit = {
      timer.start()
    }

    override def dispose(): Unit = {
      timer.stop()
    }

    object Presentation extends StatusBarWidget.IconPresentation {
      override def getIcon: Icon = if (connectionsActive) IconRunning else IconStopped

      override def getClickConsumer: Consumer[MouseEvent] = ClickConsumer

      private object ClickConsumer extends Consumer[MouseEvent] {
        override def consume(event: MouseEvent): Unit = toggleList(event)
      }

      override def getTooltipText: String = BspBundle.message("bsp.widget.bsp.connection")
    }

    //noinspection ReferencePassedToNls
    private class CloseBspSession(uri: URI, config: BspServerConfig) extends AnAction(uri.toString, BspBundle.message("bsp.widget.kill.bsp.connection.at.uri", uri), AllIcons.Actions.Suspend) with DumbAware {
      override def update(e: AnActionEvent): Unit = {
        val isAlive = BspCommunicationService.getInstance.isAlive(uri, config)
        e.getPresentation.setEnabled(isAlive)
      }

      override def actionPerformed(e: AnActionEvent): Unit = {
        BspCommunicationService.getInstance.closeCommunication(uri, config)
      }
    }

    class CloseAllSessions extends AnAction(BspBundle.message("bsp.widget.stop.all.bsp.connections"), BspBundle.message("bsp.widget.stop.all.bsp.connections"), AllIcons.Actions.Suspend) with DumbAware {

      override def update(e: AnActionEvent): Unit = {
        e.getPresentation.setEnabled(connectionsActive)
      }

      override def actionPerformed(e: AnActionEvent): Unit = {
        BspCommunicationService.getInstance.closeAll
      }
    }

    class TerminateServer(uri: URI, config: BspServerConfig) extends AnAction(BspBundle.message("bsp.widget.bsp.terminate.server", uri), BspBundle.message("bsp.widget.bsp.terminate.server", uri), AllIcons.Actions.Exit) with DumbAware {
      override def update(e: AnActionEvent): Unit = {
        val exitCommandDefined  = BspCommunicationService.getInstance.exitCommands(uri, config).toOption.exists(_.nonEmpty)
        e.getPresentation.setVisible(exitCommandDefined)
      }

      override def actionPerformed(e: AnActionEvent): Unit = try {
        val commands = BspCommunicationService.getInstance.exitCommands(uri, config).getOrElse(List())
        BspCommunicationService.getInstance.closeAll
        ApplicationManager.getApplication.executeOnPooledThread(
          new Runnable {
            override def run(): Unit = {
              if (commands.isEmpty) {
                logger.info(s"BSP server exit command not set in '${BspCommunication.argvExit}' field")
              } else {
                logger.info(s"Running BSP server restart")
              }
              val timeoutLength = 30
              commands.foreach { command =>
                logger.info(s"Running comand: '${command.mkString(" ")}'")
                val process = new ProcessBuilder(command.asJava).start()
                val timedOut = !process.waitFor(timeoutLength, TimeUnit.SECONDS)
                if (timedOut) {
                  // We log only timouts, and skip regular errors because they occur too often,
                  // ex. when the server is just not running.
                  val title = s"Timeout when terminating BSP server after ${timeoutLength} seconds"
                  val stderr = s"[STDERR]\n${IOUtils.toString(process.getErrorStream, "UTF-8")}\n"
                  val stdout = s"[STDOUT]\n${IOUtils.toString(process.getInputStream, "UTF-8")}\n"
                  logger.error(s"$title\n $stderr\n $stdout")
                }
              }
            }
          }
        )
      } catch {
        case e: Throwable => logger.error(e)
      }
    }

    private def toggleList(e: MouseEvent): Unit = {
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
  }

  override def getWidget(project: Project): StatusBarWidget = {
    if (BspUtil.isBspProject(project)) new Widget(project)
    else null
  }

  override def getAnchor: String = Anchors.before("Position")
}
