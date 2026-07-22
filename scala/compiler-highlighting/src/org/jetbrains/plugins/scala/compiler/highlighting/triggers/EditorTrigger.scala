package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.HighlightingTriggerService
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.BaseEvent

/**
 * Compiles the file the user has just landed in, either because its editor was opened or because its tab was
 * selected.
 */
private[highlighting] object EditorTrigger:
  def triggerOnFile(project: Project, virtualFile: VirtualFile, debugReason: String): Unit =
    HighlightingTriggerService(project).bypass(virtualFile, s"[${virtualFile.getName}] $debugReason")

  def triggerOnSelectedEditor(project: Project, debugReason: String): Unit =
    executeOnBackgroundThreadInNotDisposed(project) {
      Option(FileEditorManager.getInstance(project).getSelectedEditor)
        .flatMap(editor => Option(editor.getFile))
        .fold(Tracing(project).instant(BaseEvent(name = s"dropped (no editor selected):$debugReason").closed())) { file =>
          triggerOnFile(project, file, debugReason)
        }
    }