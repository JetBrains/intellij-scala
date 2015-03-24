package org.jetbrains.sbt.project.notifications

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.{EditorNotifications, EditorNotificationPanel}
import org.jetbrains.sbt.SbtBundle

/**
 * @author Nikolay Obedin
 * @since 3/24/15.
 */
object SbtNoImportNotificationProvider {
  val ProviderKey = Key.create[EditorNotificationPanel](this.getClass.getName)
}

class SbtNoImportNotificationProvider(project: Project, notifications: EditorNotifications)
        extends SbtImportNotificationProvider(project, notifications) {

  override def getKey: Key[EditorNotificationPanel] = SbtNoImportNotificationProvider.ProviderKey

  override def shouldShowPanel(file: VirtualFile, fileEditor: FileEditor): Boolean =
    getProjectSettings(file).fold(true)(_ => false)

  override def createPanel(file: VirtualFile): EditorNotificationPanel = {
    val panel = new EditorNotificationPanel()
    panel.setText(SbtBundle("sbt.notification.noimport.msg", file.getName))
    panel.createActionLabel(SbtBundle("sbt.notification.importProject"), new Runnable {
      override def run() = {
        importProject(file)
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