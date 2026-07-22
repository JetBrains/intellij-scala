package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.HighlightingTriggerPhaseEvent
import org.jetbrains.plugins.scala.compiler.highlighting.services.BackgroundExecutorService.executeOnBackgroundThreadInNotDisposed
import org.jetbrains.plugins.scala.compiler.highlighting.services.{DocumentCompilerAvailabilityService, ExternalHighlightersService}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.extensions.{PsiFileExt, inReadAction, invokeAndWait}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*

private[highlighting] object OnSettingChangeTrigger {
  def trigger(project: Project, root: PsiElement, setting: FileHighlightingSetting): Unit = {
    if (!root.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return

    executeOnBackgroundThreadInNotDisposed(project) {
      val psiFile = inReadAction(root.getContainingFile)
      if (psiFile ne null) {
        val virtualFile = psiFile.getVirtualFile
        // File could be deleted (this code is called in background activity)
        if ((virtualFile ne null) && virtualFile.isValid) {
          val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))

          invokeAndWait {
            EditorFactory.getInstance().getEditors(document).foreach { editor =>
              UpdateHighlightersUtil.setHighlightersToEditor(
                project, document,
                0, document.getTextLength, Seq.empty.asJava,
                editor.getColorsScheme, ExternalHighlightersService.ScalaCompilerPassId
              ): @nowarn("cat=deprecation")
            }
          }

          executeOnBackgroundThreadInNotDisposed(project) {
            // File could be deleted (this code is called in background activity)
            if (virtualFile.isValid) {
              WolfTheProblemSolver.getInstance(project).clearProblemsFromExternalSource(
                virtualFile, ExternalHighlightersService.instance(project)
              )
            }
          }

          if (TriggerUtil.isHighlightingEnabled && TriggerUtil.isHighlightingEnabledFor(psiFile, virtualFile, project)) {
            val debugReason = s"FileHighlightingSetting changed for ${virtualFile.getCanonicalPath}"
            val id = TriggerPhaseEvents.newRequestId()
            val tracer = Tracing(project)

            tracer.instant(HighlightingTriggerPhaseEvent(id, debugReason))

            val dispatched = if (psiFile.isScalaWorksheet) {
              val isFirstTime = !DocumentCompilerAvailabilityService(project).isAvailableFor(virtualFile)
              TriggerUtil.doTriggerWorksheetCompilation(project, virtualFile, psiFile.asInstanceOf[ScalaFile], document, isFirstTime, debugReason, id)
            } else {
              TriggerUtil.doTriggerIncrementalCompilation(project, debugReason, virtualFile, document, psiFile, id)
            }

            if (!dispatched) tracer.instant(EndEvent(id, "no compilation scheduled"))
          }
        }
      }
    }
  }
}