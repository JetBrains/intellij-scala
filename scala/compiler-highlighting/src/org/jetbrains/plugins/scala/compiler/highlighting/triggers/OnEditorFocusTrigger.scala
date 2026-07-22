package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.DocumentCompilerAvailabilityService
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.extensions.{PsiFileExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

private[highlighting] object OnEditorFocusTrigger {
  def trigger(project: Project, virtualFile: VirtualFile, requestId: RequestId): Unit =
    executeOnBackgroundThreadInNotDisposed(project) {
      val dispatched = TriggerUtil.isHighlightingEnabled &&
        ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project) &&
        virtualFile.isValid && {
        val psiFile = inReadAction(PsiManager.getInstance(project).findFile(virtualFile))
        (psiFile ne null) && TriggerUtil.isHighlightingEnabledFor(psiFile, virtualFile, project) && {
          val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
          (document ne null) && {
            val debugReason = s"focused editor changed: ${virtualFile.getName}"

            if (psiFile.isScalaWorksheet) {
              val isFirstTime = !DocumentCompilerAvailabilityService(project).isAvailableFor(virtualFile)
              TriggerUtil.doTriggerWorksheetCompilation(project, virtualFile, psiFile.asInstanceOf[ScalaFile], document, isFirstTime, debugReason, requestId)
            } else {
              TriggerUtil.doTriggerIncrementalCompilation(project, debugReason, virtualFile, document, psiFile, requestId)
            }
          }
        }
      }

      if (!dispatched) {
        Tracing(project).instant(EndEvent(requestId, "focused editor not eligible for compilation"))
      }
    }
}