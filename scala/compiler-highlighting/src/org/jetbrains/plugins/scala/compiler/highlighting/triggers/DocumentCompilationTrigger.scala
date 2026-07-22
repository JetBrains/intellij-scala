package org.jetbrains.plugins.scala.compiler.highlighting.triggers

import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.services.CompilerHighlightingService
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{CompilationRequest, PostBuildDocumentRequest, SharedClientDocumentRequest}
import org.jetbrains.plugins.scala.compiler.highlighting.services.util.CompilationUtils.EligibleDocument
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiFileExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

object DocumentCompilationTrigger {
  /**
   * The Scala source files currently open in a visible editor that are eligible for document compilation.
   *
   * Filtering by the Scala language level also ensures that the module has a Scala SDK configured and that
   * the document compiler will not be called in modules which do not have Scala configured (or during
   * project import). The Scala language level of a module is derived from the configured SDK.
   */
  private def eligibleOpenDocuments(project: Project): Seq[EligibleDocument] = {
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
  ): Boolean = {
    val eligible = eligibleOpenDocuments(project)
    val service = CompilerHighlightingService.get(project)
    eligible.foreach { case EligibleDocument(module, sourceScope, document, virtualFile, psiFile) =>
      val scope = FileCompilationScope(virtualFile, module, sourceScope, document, psiFile)
      val deadline = CompilationRequest.compilationDeadline(project)
      val request = client match {
        case Some(c) => new SharedClientDocumentRequest(scope, debugReason, deadline, requestId, project, c)
        case None => new PostBuildDocumentRequest(scope, debugReason, deadline, requestId, project)
      }
      service.compile(request)
    }
    eligible.nonEmpty
  }
}
