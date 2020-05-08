package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.project.ProjectExt

object CompileServerNotifications {

  private type Jdk = (Sdk, JavaSdkVersion)

  private val title = ScalaBundle.message("scala.compile.server.title")

  def warnIfCompileServerJdkVersionTooOld(project: Project): Unit =
    for {
      (serverSdk, serverJdkVersion) <- getCompileServerJdk(project)
      (modulesSdk, modulesJdkVersion) <- getMaxModuleJdk(project)
      if !serverJdkVersion.isAtLeast(modulesJdkVersion)
    } showWarning(project, serverSdk.getName, modulesSdk.getName)

  private def showWarning(project: Project,
                          serverSdk: String,
                          modulesSdk: String): Unit = {
    val text =
      s"""Compilation problems may occur.<p/>
         |<a href="">Use JDK $modulesSdk for Compile Server</a><p/>
         |Version of the Compile Server JDK is less than
         |the maximum JDK version used in the project.<p/>
         |Compile Server JDK: $serverSdk<p/>
         |Modules JDK: $modulesSdk<p/>
         |""".stripMargin
    val listener = new FixSdkNotificationListener(project, modulesSdk)
    val warningNotification = new Notification(title, title, text, NotificationType.WARNING, listener)
    Notifications.Bus.notify(warningNotification)
  }
  
  private class FixSdkNotificationListener(project: Project, fixedSdk: String)
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
      CompileServerLauncher.stop()
    }
  }

  private def getMaxModuleJdk(project: Project): Option[Jdk] = {
    val moduleJdkVersions = project.modules.flatMap(getModuleJdk).toSet
    if (moduleJdkVersions.nonEmpty) Some(moduleJdkVersions.maxBy(_._2)) else None
  }

  private def getModuleJdk(module: Module): Option[Jdk] =
    for {
      sdk <- Option(ModuleRootManager.getInstance(module).getSdk)
      version <- getJdkVersion(sdk)
    } yield (sdk, version)

  private def getCompileServerJdk(project: Project): Option[Jdk] =
    for {
      sdk <- CompileServerLauncher.compileServerSdk(project).toOption
      version <- getJdkVersion(sdk)
    } yield (sdk, version)

  private def getJdkVersion(sdk: Sdk): Option[JavaSdkVersion] =
    Option(sdk.getSdkType).collect {
      case javaType: JavaSdk => javaType.getVersion(sdk)
    }
}
