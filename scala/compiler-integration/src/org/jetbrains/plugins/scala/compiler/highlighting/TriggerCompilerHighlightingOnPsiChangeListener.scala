package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.project.ProjectExt

private final class TriggerCompilerHighlightingOnPsiChangeListener extends ProjectManagerListener {
  override def projectOpened(project: Project): Unit = {
    val listener = new CompilerHighlightingPsiChangeListener(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, project.unloadAwareDisposable)
  }
}
