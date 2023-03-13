package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.{PsiFile, PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.plugins.scala.compiler.highlighting.TriggerCompilerHighlightingOnPsiChangeListener.PsiChangeListener
import org.jetbrains.plugins.scala.project.ProjectExt

private final class TriggerCompilerHighlightingOnPsiChangeListener extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    val listener = new PsiChangeListener(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, project.unloadAwareDisposable)
  }
}

private object TriggerCompilerHighlightingOnPsiChangeListener {

  private class PsiChangeListener(project: Project) extends PsiTreeChangeAdapter {

    private val triggerService: TriggerCompilerHighlightingService = TriggerCompilerHighlightingService.get(project)

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
          triggerService.triggerOnFileChange(psiFile, virtualFile)
        }
      }
    }
  }
}
