package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}

private class HighlightCreatedEditorListener extends EditorFactoryListener {

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    val editor = event.getEditor
    Option(editor.getProject).foreach { project =>
      val state = CompilerGeneratedStateManager.get(project).toHighlightingState
      ExternalHighlighters.applyHighlighting(project, editor, state)
    }
  }
}