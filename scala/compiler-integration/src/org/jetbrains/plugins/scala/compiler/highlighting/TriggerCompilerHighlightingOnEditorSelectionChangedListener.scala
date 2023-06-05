package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.fileEditor.{FileEditorManagerEvent, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.ToNullSafe

// cause worksheet compilation doesn't require whole project rebuild
// we start highlighting it right away on editor selected
private final class TriggerCompilerHighlightingOnEditorSelectionChangedListener(project: Project)
  extends FileEditorManagerListener {

  override def selectionChanged(event: FileEditorManagerEvent): Unit = {
    val triggerService = TriggerCompilerHighlightingService.get(project)
    event.getOldFile.nullSafe.foreach(triggerService.disableDocumentCompiler)
    event.getNewEditor.nullSafe.foreach(triggerService.triggerOnSelectedEditorChange)
  }
}
