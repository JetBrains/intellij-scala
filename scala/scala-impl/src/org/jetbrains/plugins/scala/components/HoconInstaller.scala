package org.jetbrains.plugins.scala.components

import java.io.{File, IOException}

import com.intellij.ide.plugins.{PluginManager, PluginManagerCore, PluginNode}
import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.{FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.event.HyperlinkEvent
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}


@ScheduledForRemoval(inVersion = "2019.2")
class HoconInstaller(project: Project) extends ProjectComponent with NotificationListener with FileEditorManagerListener {

  private lazy val connection = project.getMessageBus.connect()

  private val hoconId = "org.jetbrains.plugins.hocon"

  private val myNotification = new Notification(
    "Scala plugin",
    "HOCON support installed",
    s"""<p>HOCON support is now distributed as a standalone plugin.</p>
       |<p><a href="restart">Restart</a> IDEA to activate it or <a href="disable">disable</a> the plugin?</p>""".stripMargin,
    NotificationType.INFORMATION, this
  )

  override def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent): Unit = {
    notification.expire()
    event.getDescription match {
      case "restart" =>
        ApplicationManagerEx.getApplicationEx.restart(true)
      case "disable" =>
        PluginManagerCore.disablePlugin(hoconId)
    }
  }

  private def runOnce(f: => Any): Unit = {
    val marker = new File(PathManager.getSystemPath, "scala-dep.install")
    try {
      if (marker.createNewFile())
        f
    } catch {
      case _: IOException => // if we can't create the marker just skip installation
    }
  }

  private def silentlyInstallHOCON(): Unit = runOnce {
      new Task.Backgroundable(project, "Installing HOCON support") {
        override def run(indicator: ProgressIndicator): Unit = {
          val pluginDownloader = PluginDownloader.createDownloader(new PluginNode(PluginId.getId(hoconId)))
          pluginDownloader.prepareToInstall(indicator)
          pluginDownloader.install()
          ApplicationManager.getApplication.invokeLater(extensions.toRunnable {
            Notifications.Bus.notify(myNotification)
          })
        }
      }.queue()
    }

  override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit =
    if (file.getFileType == ScalaFileType.INSTANCE) {
      connection.disconnect()
      silentlyInstallHOCON()
    }

  override def initComponent(): Unit = {
    if (PluginManager.getPlugin(PluginId.getId(hoconId)) == null)
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
  }

}
