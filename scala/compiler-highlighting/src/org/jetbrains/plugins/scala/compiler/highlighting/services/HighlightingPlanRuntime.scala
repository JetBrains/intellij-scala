package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Planner.{PlanInput, PlannedWork, WorkKind}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.{CompilationState, HighlightingRuntime, ModuleKey}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.*
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.TriggerUtil
import org.jetbrains.plugins.scala.extensions.{PsiFileExt, inReadAction, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * This class is gives platform utils needed for planning:
 * what a file is, whether its editors can take a result, and how to turn
 * planned work into something executable.
 *
 */
private[highlighting] final class HighlightingPlanRuntime(project: Project) extends HighlightingRuntime:

  private val logic = CompilationLogicService(project)
  private val highlighters = ExternalHighlightersService(project)

  override def state: CompilationState = logic.snapshot

  override def inputsFor(requests: Iterable[HighlightingRequest]): Iterable[PlanInput] =
    inReadAction:
      requests.map: request =>
        PlanInput(
          request = request,
          target = ModuleKey.of(project, request.file),
          isWorksheet = Option(psiOf(request.file)).exists(_.isScalaWorksheet),
        )

  /**
   * Judged over the files this work was asked about, never over everything it would compile.
   *
   * A build's scope holds every unbuilt file in the project, most of which are closed, so asking of the
   * scope would expire almost every build and let a single completion popup anywhere hold up the rest.
   * Expired only when nothing that was asked about can show the answer, or compiler highlighting
   * was disabled after the work was asked for.
   */
  override def readiness(work: PlannedWork): RequestState =
    inReadAction:
      if !TriggerUtil.isHighlightingEnabled(project) then
        RequestState.Expired
      else if DumbService.isDumb(project) then
        RequestState.NotReady
      else
        val states = work.covers.toSeq.map { input =>
          Option(documentOf(input.request.file)).fold(RequestState.Expired)(RequestState.of(project, _))
        }

        if states.forall(_ == RequestState.Expired) then RequestState.Expired
        else if states.contains(RequestState.NotReady) then RequestState.NotReady
        else RequestState.Ready

  override def build(work: PlannedWork): Option[CompilationRequest] =
    work.kind match
      case WorkKind.Incremental(scope) =>
        if CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE then
          // The user asked for the IDE to build on save, so that is the build. Nothing of ours is
          // scheduled, and the files stop being asked about, or this would fire again on every pull.
          invokeLater:
            SaveService(project).saveDocuments(work.id)
            BuildManager.getInstance().scheduleAutoMake()
          None
        else CompilationRequest.incremental(project, scope, work.id)

      case WorkKind.Document =>
        inReadAction:
          for
            file <- work.covers.map(_.request.file).headOption
            module <- Option(TriggerUtil.moduleFor(project, file))
            document <- Option(documentOf(file))
            psiFile <- Option(psiOf(file))
          yield DocumentRequest(
            TriggerUtil.fileCompilationScope(project, file, module, document, psiFile), work.id, project
          )

      case WorkKind.Worksheet(needsModuleBuild) =>
        inReadAction:
          for
            file <- work.covers.map(_.request.file).headOption
            document <- Option(documentOf(file))
            scalaFile <- Option(psiOf(file)).collect { case file: ScalaFile => file }
          yield WorksheetRequest(
            scalaFile, file, document, needsModuleBuild, work.id, project
          )
  
  override def serveRecorded(requests: Set[HighlightingRequest]): Unit =
      requests.foreach: request =>
        highlighters.renderRecordedDiagnostics(Set(request.file), request.id)
      

  private def psiOf(file: VirtualFile): PsiFile = PsiManager.getInstance(project).findFile(file)

  private def documentOf(file: VirtualFile): Document = FileDocumentManager.getInstance().getDocument(file)
