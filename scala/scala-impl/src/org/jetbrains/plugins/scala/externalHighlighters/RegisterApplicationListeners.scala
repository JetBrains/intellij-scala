package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}

private class RegisterApplicationListeners
  extends ApplicationInitializedListener {

  import RegisterApplicationListeners._

  override def componentsInitialized(): Unit = {
    val application = ApplicationManager.getApplication
    EditorFactory.getInstance.addEditorFactoryListener(new HighlightCreatedEditorListener, application)
  }
}

object RegisterApplicationListeners {

  private class HighlightCreatedEditorListener
    extends EditorFactoryListener {

    override def editorCreated(event: EditorFactoryEvent): Unit = {
      val editor = event.getEditor
      Option(editor.getProject).foreach { project =>
        val state = CompilerGeneratedStateManager.get(project).toHighlightingState
        ExternalHighlighters.applyHighlighting(project, editor, state)
      }
    }
  }
}
