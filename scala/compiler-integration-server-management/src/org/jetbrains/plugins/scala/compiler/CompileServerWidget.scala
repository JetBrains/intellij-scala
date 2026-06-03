package org.jetbrains.plugins.scala.compiler

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, DefaultActionGroup, Separator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget}
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread

import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon
import scala.concurrent.duration.Duration

private final class CompileServerWidget(project: Project) extends StatusBarWidget
  with StatusBarWidget.IconPresentation
  with Consumer[MouseEvent]
  with CompileServerWidgetFactory.UpdateWidgetListener {

  private val connection: MessageBusConnection = ApplicationManager.getApplication.getMessageBus.connect(this)
  private var statusBar: StatusBar = _

  override def ID(): String = CompileServerWidgetFactory.ID

  override def getPresentation: StatusBarWidget.WidgetPresentation = this

  override def install(statusBar: StatusBar): Unit = {
    this.statusBar = statusBar
    connection.subscribe(CompileServerWidgetFactory.Topic, this)
  }

  override def dispose(): Unit = {}

  override def getIcon: Icon = {
    if (launcher.running) CompileServerWidgetFactory.IconRunning
    else CompileServerWidgetFactory.IconStopped
  }

  override def getClickConsumer: Consumer[MouseEvent] = this

  //noinspection ReferencePassedToNls
  override def getTooltipText: String = {
    val portDetail = launcher.compileServerPort.map(p => s"TCP $p")
    val pidDetail = launcher.pid.map(p => s"PID $p")
    val details = portDetail ++ pidDetail
    val detailsText = if (details.isEmpty) "" else details.mkString(" (", ", ", ")")
    title + detailsText
  }

  override def consume(e: MouseEvent): Unit = {
    val mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS
    val group = new DefaultActionGroup(Start, Stop, Separator.getInstance, Configure)
    val context = DataManager.getInstance.getDataContext(e.getComponent)
    val popup = JBPopupFactory.getInstance.createActionGroupPopup(title, group, context, mnemonics, true)
    val dimension = popup.getContent.getPreferredSize
    val at = new Point(0, -dimension.height)
    popup.show(new RelativePoint(e.getComponent, at))
  }

  override def updateWidget(): Unit = {
    if (statusBar != null) {
      statusBar.updateWidget(ID())
    }
  }

  private def launcher: CompileServerLauncher.type = CompileServerLauncher

  @Nls
  private def title: String = CompilerIntegrationBundle.message("scala.compile.server.title")

  private object Start extends AnAction(CompilerIntegrationBundle.message("action.run"), CompilerIntegrationBundle.message("start.compile.server"), AllIcons.Actions.Execute) with DumbAware {
    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(!launcher.running)

    override def actionPerformed(e: AnActionEvent): Unit =
      executeOnPooledThread(launcher.ensureServerRunning(project))

    override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
  }

  private object Stop extends AnAction(CompilerIntegrationBundle.message("action.stop"), CompilerIntegrationBundle.message("shutdown.compile.server"), AllIcons.Actions.Suspend) with DumbAware {
    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(launcher.running)

    override def actionPerformed(e: AnActionEvent): Unit =
      executeOnPooledThread(launcher.stopServerAndWaitFor(Duration.Zero))

    override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
  }

  private object Configure extends AnAction(CompilerIntegrationBundle.message("action.configure"), CompilerIntegrationBundle.message("configure.compile.server"), AllIcons.General.Settings) with DumbAware {
    override def actionPerformed(e: AnActionEvent): Unit =
      CompileServerSettingsUtil.showCompileServerSettingsDialog(project)

    override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
  }
}
