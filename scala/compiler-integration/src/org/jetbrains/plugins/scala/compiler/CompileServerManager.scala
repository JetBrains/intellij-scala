package org.jetbrains.plugins.scala.compiler

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, DefaultActionGroup, Separator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon.Position
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, WindowManager}
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.{MessageBusConnection, Topic}
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.compiler.CompileServerManager._
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ShowSettingsUtilImplExt}

import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon

@Service(Array(Service.Level.PROJECT))
final class CompileServerManager(project: Project) extends Disposable with CompileServerManager.ErrorListener {

  private val IconRunning = Icons.COMPILE_SERVER
  private val IconStopped = IconLoader.getDisabledIcon(IconRunning)

  private var installed = false

  private var connection: MessageBusConnection = _

  private val errorNotificationUpdateQueue: MergingUpdateQueue =
    new MergingUpdateQueue("ErrorNotificationQueue", 1000, true, MergingUpdateQueue.ANY_COMPONENT, this)

  { // init
    if (!ApplicationManager.getApplication.isUnitTestMode) {
      configureWidget()
      connection = project.getMessageBus.connect()
      connection.subscribe(CompileServerManager.ServerStatusTopic, Widget)
      connection.subscribe(CompileServerManager.ErrorTopic, this)
    }
  }

  override def dispose(): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      return

    connection.dispose()
    removeWidget()
  }

  private def applicable: Boolean = running ||
    ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED &&
      project.hasScala

  private def running: Boolean = launcher.running

  private def launcher = CompileServerLauncher

  private def statusBar = Option(WindowManager.getInstance.getStatusBar(project))

  @Nls
  private def title = CompilerIntegrationBundle.message("scala.compile.server.title")
  private val NotificationGroupId = "Scala Compile Server"

  private def configureWidget(): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) return

    (applicable, installed) match {
      case (false, false) => // do nothing
      case (true, true) => // do nothing
      case (true, false) =>
        statusBar.foreach { b =>
          //noinspection ApiStatus
          b.addWidget(Widget, LoadingOrder.before("Position").toString, project.unloadAwareDisposable)
          installed = true
        }
      case (false, true) =>
        removeWidget()
    }
  }

  private def removeWidget(): Unit = {
    if (installed) {
      //noinspection ApiStatus
      statusBar.foreach(_.removeWidget(Widget.ID))
      Widget.dispose()
      installed = false
    }
  }

  private def updateWidget(): Unit = {
    statusBar.foreach(_.updateWidget(Widget.ID))
  }

  private object Widget extends StatusBarWidget
    with StatusBarWidget.IconPresentation
    with Consumer[MouseEvent]
    with ServerStatusListener {

    private var icon: Icon = IconStopped

    override def ID = "Compile server"

    override def getPresentation: StatusBarWidget.WidgetPresentation = this

    override def install(statusBar: StatusBar): Unit = {}

    override def dispose(): Unit = {
      icon = null
    }

    override def getIcon: Icon = icon

    override def getClickConsumer: Consumer[MouseEvent] = this

    //noinspection ReferencePassedToNls
    override def getTooltipText: String = {
      val portDetail = launcher.port.map(p => s"TCP $p")
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

    override def onServerStatus(running: Boolean): Unit = {
      icon = if (running) IconRunning else IconStopped
      updateWidget()
    }
  }

  private object Start extends AnAction(CompilerIntegrationBundle.message("action.run"), CompilerIntegrationBundle.message("start.compile.server"), AllIcons.Actions.Execute) with DumbAware {
    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(!launcher.running)

    override def actionPerformed(e: AnActionEvent): Unit =
      executeOnPooledThread(launcher.ensureServerRunning(project))
  }

  private object Stop extends AnAction(CompilerIntegrationBundle.message("action.stop"), CompilerIntegrationBundle.message("shutdown.compile.server"), AllIcons.Actions.Suspend) with DumbAware {
    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(launcher.running)

    override def actionPerformed(e: AnActionEvent): Unit =
      executeOnPooledThread(launcher.stop())
  }

  private object Configure extends AnAction(CompilerIntegrationBundle.message("action.configure"), CompilerIntegrationBundle.message("configure.compile.server"), AllIcons.General.Settings) with DumbAware {
    override def actionPerformed(e: AnActionEvent): Unit =
      showCompileServerSettingsDialog(project)
  }

  private val errorsBuffer: java.lang.StringBuilder = new java.lang.StringBuilder()

  private val showNotificationUpdate: Update = new Update(this) {
    override def run(): Unit = {
      val text = synchronized {
        val text = errorsBuffer.toString
        errorsBuffer.setLength(0)
        text
      }
      val message = text.replace(System.lineSeparator(), "<br/>")
      showNotification(message, NotificationType.ERROR)
    }
  }

  override def onError(errorsText: String): Unit = {
    synchronized(errorsBuffer.append(errorsText))
    errorNotificationUpdateQueue.queue(showNotificationUpdate)
  }

  def showNotification(@Nls message: String, notificationType: NotificationType): Unit = {
    Notifications.Bus.notify(new Notification(NotificationGroupId, title, message, notificationType), project)
  }

  @RequiresEdt
  def showStoppedByIdleTimoutNotification(): Unit = {
    val message = NlsString(CompilerIntegrationBundle.message("compile.server.stopped.due.to.inactivity"))
    showBalloonNotificationOnWidget(message, Widget, project)
  }
}

object CompileServerManager {

  private[compiler] trait ErrorListener {
    def onError(text: String): Unit
  }

  private[compiler] val ErrorTopic: Topic[ErrorListener] =
    new Topic("Scala compile server errors text topic", classOf[ErrorListener])

  private[compiler] trait ServerStatusListener {
    def onServerStatus(running: Boolean): Unit
  }

  private[compiler] val ServerStatusTopic: Topic[ServerStatusListener] =
    new Topic("Scala compile server status topic", classOf[ServerStatusListener])

  def apply(project: Project): CompileServerManager =
    project.getService(classOf[CompileServerManager])

  private[compiler] def init(project: Project): CompileServerManager = apply(project)

  def showCompileServerSettingsDialog(project: Project, filter: String = ""): Unit =
    ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[ScalaCompileServerForm], filter)

  def enableCompileServer(): Unit = {
    val settings = ScalaCompileServerSettings.getInstance()
    settings.COMPILE_SERVER_ENABLED = true
  }

  /**
   * This method only shows the balloon, but doesn't log it in the "Event Log" tool window.
   *
   * Current IntelliJ API doesn't support showing real Notifications on widgets (like on tool windows)
   * and you can add a "Event Log" entry only via a `com.intellij.notification.Notification` object.
   *
   * TODO: rewrite with proper api when IDEA-273990 is fixed
   */
  private def showBalloonNotificationOnWidget(message: NlsString, widget: StatusBarWidget, project: Project): Unit = {
    val balloonBuilder = JBPopupFactory.getInstance.createHtmlTextBalloonBuilder(message.nls, MessageType.INFO, null)
    val balloon = balloonBuilder.createBalloon()

    val statusBar = Option(WindowManager.getInstance.getStatusBar(project))
    val positionTracker = statusBar match {
      case Some(bar: IdeStatusBarImpl) if bar.getComponent.isVisible =>
        val component = Option(bar.getWidgetComponent(widget.ID()))
        component.map { c =>
          new PositionTracker[Balloon](c) {
            override def recalculateLocation(b: Balloon): RelativePoint =
              new RelativePoint(c, new Point(c.getWidth / 2, 0))
          }
        }
      case _ =>
        // if status bar is not visible, show the balloon in the right-bottom corner
        val component = Option(WindowManager.getInstance.getIdeFrame(project)).map(_.getComponent)
        component.map { c =>
          new PositionTracker[Balloon](c) {
            override def recalculateLocation(b: Balloon): RelativePoint = {
              new RelativePoint(c, new Point(c.getWidth, c.getHeight))
            }
          }
        }
    }

    positionTracker.foreach(balloon.show(_, Position.above))
  }

}
