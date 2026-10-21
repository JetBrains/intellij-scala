package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{RequestId, newRequestId}
import org.jetbrains.plugins.scala.compiler.highlighting.services.HighlightingService
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{PostBuildDocumentRequest, SharedClientDocumentRequest}
import org.jetbrains.plugins.scala.compiler.highlighting.services.util.CompilationUtils.EligibleDocument
import org.jetbrains.plugins.scala.compiler.highlighting.util.TracingUtil.endTrace
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.BaseEvent
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiFileExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.util.{Failure, Success, Try}

object DocumentCompilationTrigger:
  /**
   * The Scala source files currently open in a visible editor that are eligible for document compilation.
   *
   * Filtering by the Scala language level also ensures that the module has a Scala SDK configured and that
   * the document compiler will not be called in modules which do not have Scala configured (or during
   * project import). The Scala language level of a module is derived from the configured SDK.
   */
  private def eligibleOpenDocuments(project: Project): Seq[EligibleDocument] =
    val selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles
    selectedFiles.toSeq.flatMap { vf =>
      val (document, module, psiFile) = inReadAction {
        (
          FileDocumentManager.getInstance().getDocument(vf),
          ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(vf),
          PsiManager.getInstance(project).findFile(vf)
        )
      }
      Option.when(
        document != null && module != null && psiFile != null &&
          psiFile.is[ScalaFile] && !psiFile.isScalaWorksheet && module.hasScala &&
          (module.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_3_3) || ScalaProjectSettings.in(project).isUseCompilerTypes)
      ) {
        EligibleDocument(module, FileCompilationScope.sourceScopeOf(project, vf), document, vf, psiFile)
      }
    }

  /**
   * Compiles every Scala source file open in a visible editor.
   *
   * When `client` is defined, each document is compiled within that ongoing compilation (used right after
   * an incremental JPS/BSP compilation, to also refresh the open documents in the same session). When it is
   * empty, there is no ongoing compilation to reuse (e.g. after an IDE-driven JPS build finishes), so each
   * eligible file gets its own compilation.
   *
   * @return `true` if at least one document compilation was dispatched for `requestId`.
   */
  def triggerDocumentCompilationInAllOpenEditors(
    project: Project,
    debugReason: String,
    requestId: RequestId,
    client: Option[CompilerEventGeneratingClient] = None
  ): Boolean =
    val eligible = eligibleOpenDocuments(project)

    if (eligible.isEmpty) {
      Tracing(project).endTrace(requestId, "No document compilation needed after build")
      false
    } else {
      val lastIndex = eligible.size - 1
      eligible.zipWithIndex.foreach { case (EligibleDocument(module, sourceScope, document, virtualFile, psiFile), index) =>
        val scope = FileCompilationScope(virtualFile, module, sourceScope, document, psiFile)
        val isLast = index == lastIndex
        val fileRequestId = newRequestId()
        Tracing(project).instant(BaseEvent(s"Compiling file ${virtualFile.getPresentableName}",
          parentKey = Some(requestId), key = Some(fileRequestId), closeParent = false, closeOnEnd = true))
        val request = client match {
          case Some(c) => new SharedClientDocumentRequest(scope, debugReason, fileRequestId, project, c, closeParent = true)
          case None => new PostBuildDocumentRequest(scope, debugReason, fileRequestId, project, closeParent = true)
        }

        Try:
          HighlightingService(project).compile(request)
        match
          case Failure(exception) => Tracing(project).endTrace(fileRequestId, "Compilation Failed")
          case Success(value) => ()
      }
      Tracing(project).endTrace(requestId, "Document compilations dispatched or executed")
      true
    }
