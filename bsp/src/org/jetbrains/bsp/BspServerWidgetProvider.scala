package org.jetbrains.bsp


import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetProvider, WindowManager}
import java.awt.Point
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, DefaultActionGroup, Separator}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar.Anchors
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import javax.swing.{Icon, Timer}
import org.jetbrains.bsp.protocol.BspCommunicationService

class BspServerWidgetProvider extends StatusBarWidgetProvider{

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

    def connectionActive: Boolean = {
      val inst = BspCommunicationService.getInstance
      inst.communicate(project).alive
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
      override def getIcon: Icon = if (connectionActive) IconRunning else IconStopped

      override def getClickConsumer: Consumer[MouseEvent] = ClickConsumer

      private object ClickConsumer extends Consumer[MouseEvent] {
        override def consume(event: MouseEvent): Unit = toggleList(event)
      }

      override def getTooltipText: String = "BSP Connection"
    }

    private object Stop extends AnAction("&Stop", "Kill BSP connection", AllIcons.Actions.Suspend) with DumbAware {
      override def update(e: AnActionEvent): Unit = {
        e.getPresentation.setEnabled(connectionActive)
      }

      override def actionPerformed(e: AnActionEvent): Unit = {
        BspCommunicationService.getInstance.communicate(project).closeSession()
      }
    }

    private def toggleList(e: MouseEvent): Unit = {
      val mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS
      val group = new DefaultActionGroup(Stop, Separator.getInstance)
      val context = DataManager.getInstance.getDataContext(e.getComponent)
      val title =  s"BSP Connection (${if(connectionActive) "on" else "off"})"
      val popup = JBPopupFactory.getInstance.createActionGroupPopup(title, group, context, mnemonics, true)
      val dimension = popup.getContent.getPreferredSize
      val at = new Point(0, -dimension.height)
      popup.show(new RelativePoint(e.getComponent, at))
    }
  }


  override def getWidget(project: Project): StatusBarWidget = new Widget(project)

  override def getAnchor: String = Anchors.before("Position")
}
