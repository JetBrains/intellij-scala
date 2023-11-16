package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private final class CompilerHighlightingFocusChangeListener extends FocusChangeListener {
  override def focusGained(editor: Editor): Unit = {
    withProjectAndVirtualFile(editor) { (project, file) =>
      TriggerCompilerHighlightingService.get(project).triggerOnEditorFocus(file)
    }
  }

  override def focusLost(editor: Editor): Unit = {
    withProjectAndVirtualFile(editor) { (project, file) =>
      TriggerCompilerHighlightingService.get(project).disableDocumentCompiler(file)
    }
  }

  private def withProjectAndVirtualFile(editor: Editor)(action: (Project, VirtualFile) => Unit): Unit = {
    for {
      project <- Option(editor.getProject)
      file <- Option(editor.getVirtualFile)
    } action(project, file)
  }
}
