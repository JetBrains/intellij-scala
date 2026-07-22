package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.DocumentCompilerAvailabilityService
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.extensions.{PsiFileExt, PsiNamedElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

private[highlighting] object OnFileChangeTrigger {
  def trigger(project: Project, psiFile: PsiFile, virtualFile: VirtualFile, requestId: RequestId): Unit =
    executeOnBackgroundThreadInNotDisposed(project) {
      val process = TriggerUtil.isHighlightingEnabled &&
        !virtualFile.isInstanceOf[VirtualFileWindow] &&
        virtualFile.isValid &&
        TriggerUtil.isHighlightingEnabledFor(psiFile, virtualFile, project)

      val dispatched = process && {
        val debugReason = s"file content changed: ${psiFile.name}"
        val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
        (document ne null) && {
          val availability = DocumentCompilerAvailabilityService(project)

          if (psiFile.isScalaWorksheet) {
            val isFirstTime = !availability.isAvailableFor(virtualFile)
            TriggerUtil.doTriggerWorksheetCompilation(project, virtualFile, psiFile.asInstanceOf[ScalaFile], document, isFirstTime, debugReason, requestId)
          } else if (availability.isAvailableFor(virtualFile)) {
            TriggerUtil.doTriggerDocumentCompilation(project, virtualFile, document, psiFile, debugReason, requestId)
          } else {
            TriggerUtil.doTriggerIncrementalCompilation(project, debugReason, virtualFile, document, psiFile, requestId)
          }
        }
      }

      if (!dispatched) {
        Tracing(project).instant(EndEvent(requestId, "no compilation scheduled for file change"))
      }
    }
}