package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import org.jetbrains.plugins.scala.extensions._

private class HighlightCreatedEditorListener extends EditorFactoryListener {

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    val editor = event.getEditor
    for (project <- editor.getProject.nullSafe) {
      TriggerCompilerHighlightingService.get(project).triggerOnEditorCreated(editor)
    }
  }
}