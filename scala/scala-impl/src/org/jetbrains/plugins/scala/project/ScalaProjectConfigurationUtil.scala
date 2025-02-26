package org.jetbrains.plugins.scala.project

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import org.jetbrains.plugins.scala.ScalaFileType

object ScalaProjectConfigurationUtil {
  def refreshEditorNotifications(project: Project): Unit = {
    val openFiles = FileEditorManager.getInstance(project).getOpenFiles
    val openScalaFiles = openFiles.filter(FileTypeRegistry.getInstance.isFileOfType(_, ScalaFileType.INSTANCE))
    if (openScalaFiles.isEmpty) return

    val editorNotifications = EditorNotifications.getInstance(project)
    openScalaFiles.foreach(editorNotifications.updateNotifications)
  }
}
