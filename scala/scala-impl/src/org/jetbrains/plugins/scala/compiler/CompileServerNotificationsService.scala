package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.CompositeModificationTracker
import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.nowarn
import scala.concurrent.duration.DurationLong

@Service
final class CompileServerNotificationsService(project: Project) {

  private val title = ScalaBundle.message("scala.compile.server.title")
  
  private val modificationTracker = new CompositeModificationTracker(ProjectRootManager.getInstance(project))
  
  def resetNotifications(): Unit =
    modificationTracker.incModificationCount()

  /**
   * SCL-18187
   * SCL-17817
   */
  @nowarn("msg=pure expression")
  @Cached(modificationTracker, null)
  def warnIfCompileServerJdkMayLeadToCompilationProblems(): Unit = if (project.hasScala) {
    def serverJdkIsOk(serverJdkVersion: JavaSdkVersion, recommendedJdkVersion: JavaSdkVersion): Boolean =
      if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project))
        serverJdkVersion == recommendedJdkVersion
      else
        serverJdkVersion isAtLeast recommendedJdkVersion
    for {
      (serverSdk, serverJdkVersion) <- CompileServerJdkManager.compileServerJdk(project)
      (recommendedSdk, recommendedJdkVersion) <- CompileServerJdkManager.recommendedJdk(project)
      if !serverJdkIsOk(serverJdkVersion, recommendedJdkVersion)
    } showWarning(serverSdk.getName, recommendedSdk.getName)
  }

  private def showWarning(serverSdk: String, recommendedSdk: String): Unit = {
    val text =
      s"""Compilation problems may occur.<p/>
         |We recommend <a href="">using JDK $recommendedSdk</a> to prevent them.<p/>
         |Current JDK is $serverSdk.
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
    project.getService(classOf[CompileServerNotificationsService])
}
