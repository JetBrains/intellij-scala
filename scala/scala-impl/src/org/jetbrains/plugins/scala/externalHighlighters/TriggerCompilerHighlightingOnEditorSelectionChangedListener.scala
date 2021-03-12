package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.fileEditor.{FileEditorManagerEvent, FileEditorManagerListener}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.ToNullSafe

// cause worksheet compilation doesn't require whole project rebuild
// we start highlighting it right away on editor selected
class TriggerCompilerHighlightingOnEditorSelectionChangedListener(project: Project)
  extends FileEditorManagerListener {

  override def selectionChanged(event: FileEditorManagerEvent): Unit =
    event.getNewEditor.nullSafe
      .foreach(TriggerCompilerHighlightingService.get(project).triggerOnSelectionChange)
}

object TriggerCompilerHighlightingOnEditorSelectionChangedListener
