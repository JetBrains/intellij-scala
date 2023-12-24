package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.startup.ProjectActivity

private final class CompilerHighlightingSetupActivity extends ProjectActivity {
  override def execute(project: Project): Unit = {
    val fileListener = new CompilerHighlightingFileListener(project)
    VirtualFileManager.getInstance().addAsyncFileListener(fileListener, project.unloadAwareDisposable)
    val psiChangeListener = new CompilerHighlightingPsiChangeListener(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(psiChangeListener, project.unloadAwareDisposable)
  }
}
