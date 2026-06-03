package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiFile, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.psi.impl.compiled.ClsFileImpl

private class CompilerHighlightingPsiChangeListener(project: Project) extends PsiTreeChangeAdapter {
  override def childrenChanged(event: PsiTreeChangeEvent): Unit = {
    triggerOnFileChange(event.getFile)
  }

  override def childRemoved(event: PsiTreeChangeEvent): Unit = {
    if (event.getFile eq null) {
      val child = event.getChild
      child match {
        case null | _: ClsFileImpl => ()
        case _ => triggerOnFileChange(child.getContainingFile)
      }
    }
  }

  private[this] def triggerOnFileChange(psiFile: PsiFile): Unit = {
    if (psiFile ne null) {
      val virtualFile = psiFile.getVirtualFile
      if (virtualFile ne null) {
        TriggerCompilerHighlightingService.get(project).triggerOnFileChange(psiFile, virtualFile)
      }
    }
  }
}
