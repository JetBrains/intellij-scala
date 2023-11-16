package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.project.ProjectExt

private final class TriggerCompilerHighlightingOnPsiChangeListener extends ProjectManagerListener {
  override def projectOpened(project: Project): Unit = {
    val psiChangeListener = new CompilerHighlightingPsiChangeListener(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(psiChangeListener, project.unloadAwareDisposable)
    val focusChangeListener = new CompilerHighlightingFocusChangeListener()
    EditorFactory.getInstance().getEventMulticaster.asInstanceOf[EditorEventMulticasterEx]
      .addFocusChangeListener(focusChangeListener, project.unloadAwareDisposable)
  }
}
