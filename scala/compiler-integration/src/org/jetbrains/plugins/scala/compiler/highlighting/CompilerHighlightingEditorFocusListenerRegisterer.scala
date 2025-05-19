package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFileBase
import org.jetbrains.plugins.scala.startup.ProjectActivity
import org.jetbrains.plugins.scala.util.UnloadAwareDisposable

import java.util.concurrent.ConcurrentHashMap

/**
 * Registers a listener on all editors to trigger compiler highlighting when the editor gains focus.
 * The registration process takes dynamic loading and unloading of the plugin into account.
 */
private object CompilerHighlightingEditorFocusListenerRegisterer {
  private val holders = new ConcurrentHashMap[Editor, MyDisposable]

  private def registerIntoEditorIfApplicable(editor: Editor): Unit = {
    if (ignoreEditor(editor))
      return

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

  /**
   * List of extensions that are potentially supported by CBH
   * It contains other non-Scala JVM language extensions (java, kotlin, groovy) for mixed projects.
   * (though I suppose scala + kotlin/groovy would be a very rare case)
   */
  private val SupportedExtensions = Set(
    "scala",
    "sc",
    "scala.html", //play framework twirl templates
    //"sbt", probably no need to trigger for sbt files as we don't use CBH for sbt files anyway
    "java",
    "kt",
    "kts",
    "groovy",
  )

  //noinspection InstanceOf
  private def ignoreEditor(editor: Editor): Boolean = {
    if (editor.getProject == null)
      return true

    //The editor doesn't correspond to a physical file.
    //(examples: debugger editor, debugger condition, AIA prompt)
    //There can be dozens of such editors from many places in the IDE or installed plugins
    val virtualFile = editor.getVirtualFile
    if (virtualFile == null)
      return true

    //Sometimes editors still have some synthetic, in-memory file, also skipp them
    //(examples: editor from the injected code fragments, after you invoke "Edit <langId> fragment")
    if (virtualFile.isInstanceOf[LightVirtualFileBase])
      return true

    //Only handle known extensions that can "matter" for the compiler-based highlighting
    SupportedExtensions.contains(virtualFile.getExtension)
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
      registerIntoEditorIfApplicable(event.getEditor)
    }

    override def editorReleased(event: EditorFactoryEvent): Unit = {
      Option(holders.remove(event.getEditor)).foreach(Disposer.dispose)
    }
  }

  final class AtStartup extends ProjectActivity {
    override def execute(project: Project): Unit = {
      // pick up all existing editors... important for when dynamically loading the plugin
      for (editor <- EditorFactory.getInstance().getAllEditors) {
        registerIntoEditorIfApplicable(editor)
      }
    }
  }
}
