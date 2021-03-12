package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.psi.impl.compiled.ClsFileImpl
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ToNullSafe}
import org.jetbrains.plugins.scala.externalHighlighters.TriggerCompilerHighlightingOnPsiChangeListener.PsiChangeListener
import org.jetbrains.plugins.scala.project.ProjectExt

private class TriggerCompilerHighlightingOnPsiChangeListener
  extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    val listener = new PsiChangeListener(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, project.unloadAwareDisposable)
  }
}

object TriggerCompilerHighlightingOnPsiChangeListener {

  private class PsiChangeListener(project: Project)
    extends PsiTreeChangeAdapter {

    override def childrenChanged(event: PsiTreeChangeEvent): Unit =
      for {
        psiFile <- event.getFile.nullSafe
        virtualFile <- psiFile.getVirtualFile.nullSafe
      } TriggerCompilerHighlightingService.get(project).triggerOnFileChange(psiFile, virtualFile)

    override def childRemoved(event: PsiTreeChangeEvent): Unit =
      if (event.getFile eq null)
        for {
          child <- event.getChild.nullSafe
          if !child.is[ClsFileImpl]
          psiFile <- child.getContainingFile.nullSafe
          virtualFile <- psiFile.getVirtualFile.nullSafe
        } TriggerCompilerHighlightingService.get(project).triggerOnFileChange(psiFile, virtualFile)
  }
}
