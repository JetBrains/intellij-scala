package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.messages.Topic
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}
import org.jetbrains.plugins.scala.compiler.CompileServerManager._
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ShowSettingsUtilImplExt}
import org.jetbrains.plugins.scala.util.ScalaShutDownTracker

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
      CompileServerNotifications.showNotification(message, NotificationType.ERROR, project = None)
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
}
