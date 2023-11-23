package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}

import scala.collection.mutable

private final class CompilerHighlightingEditorFactoryListener extends EditorFactoryListener {

  private val mapping: mutable.Map[Editor, CompilerHighlightingEditorFocusListener] = mutable.Map.empty

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    val editor = event.getEditor
    val listener = new CompilerHighlightingEditorFocusListener(editor)
    mapping.put(editor, listener)
    editor.getContentComponent.addFocusListener(listener)
  }

  override def editorReleased(event: EditorFactoryEvent): Unit = {
    val editor = event.getEditor
    val listener = mapping.get(editor)
    listener.foreach { l =>
      l.focusLost()
      editor.getContentComponent.removeFocusListener(l)
      mapping.remove(editor)
    }
  }
}
