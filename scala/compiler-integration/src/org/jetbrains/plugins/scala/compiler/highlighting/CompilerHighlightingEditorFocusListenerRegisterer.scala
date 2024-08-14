package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.startup.ProjectActivity
import org.jetbrains.plugins.scala.util.UnloadAwareDisposable

import java.util.concurrent.ConcurrentHashMap

/**
 * Registers a listener on all editors to trigger compiler highlighting when the editor gains focus.
 * The registration process takes dynamic loading and unloading of the plugin into account.
 */
private object CompilerHighlightingEditorFocusListenerRegisterer {
  private val holders = new ConcurrentHashMap[Editor, MyDisposable]

  private def registerIntoEditor(editor: Editor): Unit = {
    val listener = new CompilerHighlightingEditorFocusListener(editor)
    val disposable = new MyDisposable(editor, listener)
    if (holders.putIfAbsent(editor, disposable) == null) {
      //println(s"+ register listener [$editor]")
      Disposer.register(UnloadAwareDisposable.scalaPluginDisposable, disposable)
      editor.getContentComponent.addFocusListener(listener)
    } else {
      //println(s"already registered [$editor]")
    }
  }

  private final class MyDisposable(editor: Editor, listener: CompilerHighlightingEditorFocusListener) extends Disposable {
    override def dispose(): Unit = {
      holders.remove(editor) // remove if called from unloadDisposable
      //println(s"- dispose listener[$editor]")
      listener.focusLost()
      editor.getContentComponent.removeFocusListener(listener)
    }
  }

  final class ByListener extends EditorFactoryListener {
    override def editorCreated(event: EditorFactoryEvent): Unit = {
      registerIntoEditor(event.getEditor)
    }

    override def editorReleased(event: EditorFactoryEvent): Unit = {
      Option(holders.remove(event.getEditor)).foreach(Disposer.dispose)
    }
  }

  final class AtStartup extends ProjectActivity {
    override def execute(project: Project): Unit = {
      // pick up all existing editors... important for when dynamically loading the plugin
      for (editor <- EditorFactory.getInstance().getAllEditors) {
        registerIntoEditor(editor)
      }
    }
  }
}
