package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.CompositeModificationTracker
import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import scala.concurrent.duration.DurationLong

@Service
final class CompileServerNotificationsService(project: Project) {

  private val title = ScalaBundle.message("scala.compile.server.title")
  
  private val modificationTracker = new CompositeModificationTracker(ProjectRootManager.getInstance(project))
  
  def resetNotifications(): Unit =
    modificationTracker.incModificationCount()
  
  @Cached(modificationTracker, null)
  def warnIfCompileServerJdkVersionTooOld(): Unit =
    for {
      (serverSdk, serverJdkVersion) <- CompileServerJdkManager.compileServerJdk(project)
      (recommendedSdk, recommendedJdkVersion) <- CompileServerJdkManager.recommendedJdk(project)
      if !serverJdkVersion.isAtLeast(recommendedJdkVersion)
    } showWarning(serverSdk.getName, recommendedSdk.getName)

  private def showWarning(serverSdk: String,
                          recommendedSdk: String): Unit = {
    val text =
      s"""Compilation problems may occur.<p/>
         |<a href="">Use JDK $recommendedSdk for Compile Server</a><p/>
         |Version of the Compile Server JDK is less than
         |the maximum JDK version used in the project.<p/>
         |Compile Server JDK: $serverSdk<p/>
         |Modules JDK: $recommendedSdk<p/>
         |""".stripMargin
    val listener = new FixSdkNotificationListener(recommendedSdk)
    val warningNotification = new Notification(title, title, text, NotificationType.WARNING, listener)
    Notifications.Bus.notify(warningNotification)
  }
  
  private class FixSdkNotificationListener(fixedSdk: String)
    extends NotificationListener {
    
    override def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent): Unit = {
      notification.expire()
      val settings = ScalaCompileServerSettings.getInstance
      if (CompileServerLauncher.defaultSdk(project).getName == fixedSdk) {
        settings.USE_DEFAULT_SDK = true
      } else {
        settings.USE_DEFAULT_SDK = false
        settings.COMPILE_SERVER_SDK = fixedSdk
      }
      invokeAndWait(ApplicationManager.getApplication.saveSettings())
      CompileServerLauncher.stop(timeoutMs = 3.seconds.toMillis)
      CompileServerLauncher.tryToStart(project)
    }
  }
}

object CompileServerNotificationsService {
  
  def get(project: Project): CompileServerNotificationsService =
    ServiceManager.getService(project, classOf[CompileServerNotificationsService])
}
