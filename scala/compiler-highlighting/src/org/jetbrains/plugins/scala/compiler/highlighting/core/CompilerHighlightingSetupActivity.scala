package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.compiler.highlighting.listeners.CompilerHighlightingPsiChangeListener
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.startup.ProjectActivity

private final class CompilerHighlightingSetupActivity extends ProjectActivity {
  override def execute(project: Project): Unit = {
    val disposable = project.unloadAwareDisposable
    val psiChangeListener = new CompilerHighlightingPsiChangeListener(project, disposable)
    PsiManager.getInstance(project).addPsiTreeChangeListener(psiChangeListener, disposable)
  }
}
