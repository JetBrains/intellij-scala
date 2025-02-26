package org.jetbrains.sbt.project

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.IncompleteModelUtil
import com.intellij.ui.EditorNotificationProvider
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.codeInsight.daemon.SbtProblemHighlightFilter
import org.jetbrains.sbt.language.SbtFile

import javax.swing.JComponent

private final class SbtProjectImportStateNotificationProvider extends EditorNotificationProvider {

  @RequiresReadLock
  @Nullable
  override def collectNotificationData(
    project: Project,
    file: VirtualFile
  ): java.util.function.Function[_ >: FileEditor, _ <: JComponent] = {
    val psiFile = PsiManager.getInstance(project).findFile(file)
    val sbtFile = psiFile match {
      case f: SbtFile => f
      case _ =>
        // Not an sbt file, do not show any notifications.
        return null
    }
    // The sbt file is not a part of the current project, do not show any notifications.
    if (!SbtProblemHighlightFilter.shouldHighlightSbtFile(sbtFile)) return null
    // Project reload is already in progress, do not show any notifications.
    //noinspection ApiStatus,UnstableApiUsage
    if (IncompleteModelUtil.isIncompleteModel(sbtFile)) return null
    // The project is fully imported, do not show any notifications.
    if (SbtProjectImportStateService.instance(project).isImported(sbtFile)) return null

    (fileEditor: FileEditor) => SbtProjectImportStateNotificationPanel.createNotificationPanel(project, fileEditor)
  }
}
