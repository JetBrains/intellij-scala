package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.fileEditor.{FileEditorManagerEvent, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.ToNullSafe

// cause worksheet compilation doesn't require whole project rebuild
// we start highlighting it right away on editor selected
private final class TriggerCompilerHighlightingOnEditorSelectionChangedListener(project: Project)
  extends FileEditorManagerListener {

  private val triggerService: TriggerCompilerHighlightingService = TriggerCompilerHighlightingService.get(project)

  override def selectionChanged(event: FileEditorManagerEvent): Unit = {
    event.getOldFile.nullSafe.foreach(f => triggerService.disableDocumentCompiler(f.toNioPath))
    event.getNewEditor.nullSafe.foreach(triggerService.triggerOnSelectionChange)
  }
}
