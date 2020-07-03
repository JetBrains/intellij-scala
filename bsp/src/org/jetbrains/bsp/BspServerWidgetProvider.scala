package org.jetbrains.bsp


import java.awt.Point
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import java.net.URI

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, DefaultActionGroup, Separator}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar.Anchors
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetProvider, WindowManager}
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import javax.swing.{Icon, Timer}
import org.jetbrains.bsp.protocol.BspCommunicationService

import scala.collection.JavaConverters._

class BspServerWidgetProvider extends StatusBarWidgetProvider {

  private val IconRunning = org.jetbrains.bsp.Icons.BSP
  private val IconStopped = IconLoader.getDisabledIcon(IconRunning)

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
      comService.listOpenComms.exists(comService.isAlive)
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
    private class CloseBspSession(uri: URI) extends AnAction(uri.toString, BspBundle.message("bsp.widget.kill.bsp.connection.at.uri", uri), AllIcons.Actions.Suspend) with DumbAware {
      override def update(e: AnActionEvent): Unit = {
        val isAlive = BspCommunicationService.getInstance.isAlive(uri)
        e.getPresentation.setEnabled(isAlive)
      }

      override def actionPerformed(e: AnActionEvent): Unit = {
        BspCommunicationService.getInstance.closeCommunication(uri)
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

    private def toggleList(e: MouseEvent): Unit = {
      val openCom = BspCommunicationService.getInstance.listOpenComms
      val connectionClosers =
        (openCom.map(new CloseBspSession(_)).toList
          :+ new CloseAllSessions
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
