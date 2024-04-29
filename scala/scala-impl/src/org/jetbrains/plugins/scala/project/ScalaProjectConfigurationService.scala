package org.jetbrains.plugins.scala.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ScalaFileType

@Service(Array(Service.Level.PROJECT))
final class ScalaProjectConfigurationService(private val project: Project) {
  @volatile private var syncInProgress: Boolean = false

  def isSyncInProgress: Boolean = syncInProgress

  @ApiStatus.Internal
  private[jetbrains] def onSyncStarted(): Unit = syncInProgress = true

  @ApiStatus.Internal
  private[jetbrains] def onSyncEnded(): Unit = syncInProgress = false

  def refreshEditorNotifications(): Unit = {
    val openFiles = FileEditorManager.getInstance(project).getOpenFiles
    val openScalaFiles = openFiles.filter(FileTypeRegistry.getInstance.isFileOfType(_, ScalaFileType.INSTANCE))
    if (openScalaFiles.isEmpty) return

    val editorNotifications = EditorNotifications.getInstance(project)
    openScalaFiles.foreach(editorNotifications.updateNotifications)
  }
}

object ScalaProjectConfigurationService {
  def getInstance(project: Project): ScalaProjectConfigurationService =
    project.getService(classOf[ScalaProjectConfigurationService])
}
