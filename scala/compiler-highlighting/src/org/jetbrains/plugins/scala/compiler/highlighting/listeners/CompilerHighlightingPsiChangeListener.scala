package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.{PsiFile, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.SourceRole
import org.jetbrains.plugins.scala.compiler.highlighting.services.CompilationLogicService
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.FileTrigger

private[highlighting] class CompilerHighlightingPsiChangeListener(project: Project, parent: Disposable)
  extends PsiTreeChangeAdapter {
  
  private val logic = CompilationLogicService(project)
  
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

  private def triggerOnFileChange(psiFile: PsiFile): Unit = {
    if ((psiFile ne null) && !project.isDisposed) {
      val virtualFile = psiFile.getVirtualFile
      if (virtualFile ne null) {
        recordModification(virtualFile)
        FileTrigger.trigger(project, virtualFile, "psi change")
      }
    }
  }
  
  private def recordModification(virtualFile: VirtualFile): Unit = {
    SourceRole.of(project, virtualFile) match {
      case SourceRole.Compiled => logic.fileModified(virtualFile)
      case SourceRole.BuildInput => logic.buildInputChanged()
      case SourceRole.Untracked => ()
    }
  }
}
