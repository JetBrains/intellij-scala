package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.{PsiFile, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.HighlightingTriggerPhaseEvent
import org.jetbrains.plugins.scala.compiler.tracing.Tracing

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

  private def triggerOnFileChange(psiFile: PsiFile): Unit = {
    if (psiFile ne null) {
      val virtualFile = psiFile.getVirtualFile
      if (virtualFile ne null) {
        val requestId = TriggerPhaseEvents.newRequestId()
        Tracing(project).instant(HighlightingTriggerPhaseEvent(requestId, "psi change"))
        TriggerCompilerHighlightingService.get(project).triggerOnFileChange(psiFile, virtualFile, requestId)
      }
    }
  }
}
