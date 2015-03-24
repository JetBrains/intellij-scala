package org.jetbrains.sbt.project.notifications

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.{EditorNotifications, EditorNotificationPanel}
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.settings.SbtLocalSettings

/**
 * @author Nikolay Obedin
 * @since 3/24/15.
 */
object SbtReImportNotificationProvider {
  val ProviderKey = Key.create[EditorNotificationPanel](this.getClass.getName)
}

class SbtReImportNotificationProvider(project: Project, notifications: EditorNotifications)
        extends SbtImportNotificationProvider(project, notifications) {

  override def getKey: Key[EditorNotificationPanel] = SbtReImportNotificationProvider.ProviderKey

  override def shouldShowPanel(file: VirtualFile, fileEditor: FileEditor): Boolean =
    getProjectSettings(file).fold(false) { projectSettings =>
      val stamp = Option(SbtLocalSettings.getInstance(project)).map(_.lastUpdateTimestamp).getOrElse(0L)
      !projectSettings.useOurOwnAutoImport && stamp < file.getTimeStamp
    }

  override def createPanel(file: VirtualFile): EditorNotificationPanel = {
    val panel = new EditorNotificationPanel()
    panel.setText(SbtBundle("sbt.notification.reimport.msg", file.getName))
    panel.createActionLabel(SbtBundle("sbt.notification.refreshProject"), new Runnable {
      override def run() = {
        refreshProject()
        notifications.updateAllNotifications()
      }
    })
    panel.createActionLabel(SbtBundle("sbt.notification.enableAutoImport"), new Runnable {
      override def run() = {
        getProjectSettings(file).foreach(_.setUseOurOwnAutoImport(true))
        refreshProject()
        notifications.updateAllNotifications()
      }
    })
    panel.createActionLabel(SbtBundle("sbt.notification.ignore"), new Runnable {
      override def run() = {
        ignoreFile(file)
        notifications.updateAllNotifications()
      }
    })
    panel
  }
}