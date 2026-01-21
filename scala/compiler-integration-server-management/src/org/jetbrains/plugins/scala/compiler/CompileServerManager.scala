package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon.Position
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.compiler.CompileServerManager._
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ShowSettingsUtilImplExt}
import org.jetbrains.plugins.scala.util.ScalaShutDownTracker

import java.awt.Point
import java.util.concurrent.locks.{Lock, ReentrantLock}
import scala.concurrent.duration.Duration

@Service(Array(Service.Level.APP))
final class CompileServerManager extends Disposable with CompileServerManager.ErrorListener {

  private val errorNotificationUpdateQueue: MergingUpdateQueue =
    new MergingUpdateQueue("ErrorNotificationQueue", 1000, true, MergingUpdateQueue.ANY_COMPONENT, this)

  private val errorsBuffer: java.lang.StringBuilder = new java.lang.StringBuilder()

  private val errorsBufferLock: Lock = new ReentrantLock()

  private val showNotificationUpdate: Update = new Update(this) {
    override def run(): Unit = {
      errorsBufferLock.lock()
      val text = try {
        val t = errorsBuffer.toString
        errorsBuffer.setLength(0)
        t
      } finally {
        errorsBufferLock.unlock()
      }

      @NlsSafe
      val message = text.replace(System.lineSeparator(), "<br/>")
      showNotification(message, NotificationType.ERROR, project = None)
    }
  }

  { // init
    val app = ApplicationManager.getApplication
    if (!app.isUnitTestMode) {
      val conn = app.getMessageBus.connect(this: Disposable) // Automatically released when this service is disposed
      conn.subscribe(CompileServerManager.ErrorTopic, this: CompileServerManager.ErrorListener)
    }

    ScalaShutDownTracker.registerShutdownTask(() => {
      Log.info("Shutdown event triggered, stopping server")
      CompileServerLauncher.stopServerAndWaitFor(Duration.Zero)
    })
  }

  override def dispose(): Unit = {}

  override def onError(errorsText: String): Unit = {
    errorsBufferLock.lock()
    try errorsBuffer.append(errorsText)
    finally errorsBufferLock.unlock()
    errorNotificationUpdateQueue.queue(showNotificationUpdate)
  }
}

object CompileServerManager {

  private final val Log: Logger = Logger.getInstance(classOf[CompileServerManager])

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

  def instance(): CompileServerManager =
    ApplicationManager.getApplication.getService(classOf[CompileServerManager])

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

  @Nls
  private def title: String = CompilerIntegrationBundle.message("scala.compile.server.title")

  private val NotificationGroupId = "Scala Compile Server"

  def showNotification(@Nls message: String, notificationType: NotificationType, project: Option[Project]): Unit = {
    Notifications.Bus.notify(new Notification(NotificationGroupId, title, message, notificationType), project.orNull)
  }

  @RequiresEdt
  def showStoppedByIdleTimeoutNotification(project: Project): Unit = {
    val message = NlsString(CompilerIntegrationBundle.message("compile.server.stopped.due.to.inactivity"))
    showBalloonNotificationOnWidget(message, project)
  }
}
