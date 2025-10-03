package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon.Position
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.{MessageBusConnection, Topic}
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.compiler.CompileServerManager._
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ShowSettingsUtilImplExt}

import java.awt.Point

@Service(Array(Service.Level.PROJECT))
final class CompileServerManager(project: Project) extends Disposable with CompileServerManager.ErrorListener {

  private var connection: MessageBusConnection = _

  private val errorNotificationUpdateQueue: MergingUpdateQueue =
    new MergingUpdateQueue("ErrorNotificationQueue", 1000, true, MergingUpdateQueue.ANY_COMPONENT, this)

  { // init
    if (!ApplicationManager.getApplication.isUnitTestMode) {
      connection = ApplicationManager.getApplication.getMessageBus.connect()
      connection.subscribe(CompileServerManager.ErrorTopic, this)
    }
  }

  override def dispose(): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      return

    Disposer.dispose(connection)
    connection = null
  }

  @Nls
  private def title = CompilerIntegrationBundle.message("scala.compile.server.title")
  private val NotificationGroupId = "Scala Compile Server"

  private val errorsBuffer: java.lang.StringBuilder = new java.lang.StringBuilder()

  private val errorsBufferLock: AnyRef = new Object()

  private val showNotificationUpdate: Update = new Update(this) {
    override def run(): Unit = {
      val text = errorsBufferLock.synchronized {
        val text = errorsBuffer.toString
        errorsBuffer.setLength(0)
        text
      }
      val message = text.replace(System.lineSeparator(), "<br/>")
      showNotification(message, NotificationType.ERROR)
    }
  }

  override def onError(errorsText: String): Unit = {
    errorsBufferLock.synchronized {
      errorsBuffer.append(errorsText)
    }
    errorNotificationUpdateQueue.queue(showNotificationUpdate)
  }

  def showNotification(@Nls message: String, notificationType: NotificationType): Unit = {
    Notifications.Bus.notify(new Notification(NotificationGroupId, title, message, notificationType), project)
  }

  @RequiresEdt
  def showStoppedByIdleTimoutNotification(): Unit = {
    val message = NlsString(CompilerIntegrationBundle.message("compile.server.stopped.due.to.inactivity"))
    showBalloonNotificationOnWidget(message, project)
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
  private def showBalloonNotificationOnWidget(message: NlsString, project: Project): Unit = {
    val balloonBuilder = JBPopupFactory.getInstance.createHtmlTextBalloonBuilder(message.nls, MessageType.INFO, null)
    val balloon = balloonBuilder.createBalloon()

    val statusBar = Option(WindowManager.getInstance.getStatusBar(project))
    val positionTracker = statusBar match {
      case Some(bar: IdeStatusBarImpl) if bar.getComponent.isVisible =>
        val component = Option(bar.getWidgetComponent(CompileServerWidgetFactory.ID))
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
