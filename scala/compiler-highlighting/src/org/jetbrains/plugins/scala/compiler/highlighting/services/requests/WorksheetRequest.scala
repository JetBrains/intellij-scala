package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.compilers.WorksheetHighlightingCompiler
import org.jetbrains.plugins.scala.compiler.highlighting.core.FileCompilationScope
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilationRequestPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.highlighting.services.CompilerHighlightingService
import org.jetbrains.plugins.scala.compiler.highlighting.services.util.CompilationUtils
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt
import org.jetbrains.plugins.scala.util.{CanonicalPath, DocumentVersion}

import scala.collection.immutable
import scala.concurrent.duration.Deadline

/**
 * @param isFirstTimeHighlighting whether worksheet editor has just been selected (is true every time when tabs are switched)
 */
class WorksheetRequest(
  val file: ScalaFile,
  val virtualFile: VirtualFile,
  val document: Document,
  val isFirstTimeHighlighting: Boolean,
  val debugReason: String,
  override val deadline: Deadline,
  val requestId: RequestId,
  protected val project: Project
) extends BaseCompilationRequest(
  Map(virtualFile -> document),
  deadline,
  requestId
) {
  private val Log = Logger.getInstance(classOf[WorksheetRequest])

  override val priority: Int = 1

  override def kind: CompilationKind = CompilationKind.Worksheet
  override def delayed(newDeadline: Deadline): WorksheetRequest =
    new WorksheetRequest(file, virtualFile, document, isFirstTimeHighlighting, debugReason, newDeadline, requestId, project)

  override def isReadyForExecution: RequestState = {
    if (isExpired) return RequestState.Expired

    if (deadline.isOverdue()) {
      if (DumbService.isDumb(project)) return RequestState.NotReady
      canDocumentBeCompiled(project, document)
    } else {
      RequestState.NotReady
    }
  }

  override private[services] def execute(): Unit = {
    if (Log.isDebugEnabled) {
      Log.debug(s"[${project.getName}] worksheetCompilation: $debugReason (isFirstTimeHighlighting: $isFirstTimeHighlighting)")
    }

    //Note, we don't need to invoke `findRepresentativeModuleForSharedSourceModuleOrSelf`
    //because it's already called for all worksheets in WorksheetSyntheticModuleService
    val module = file.module match {
      case Some(m) => m
      case None =>
        Log.warn(s"[${project.getName}] can't find module for worksheet ${file.name}")
        return
    }

    if (isFirstTimeHighlighting) {
      //If we have just opened worksheet we need to invoke incremental compilation to ensure that worksheet module is compiled to avoid red code
      //Otherwise if you open non-compiled project and open worksheet it will contain red code
      val scope = FileCompilationScope(virtualFile, module, FileCompilationScope.sourceScopeOf(project, virtualFile), document, file)
      val incrementalRequest = IncrementalRequest(
        Map(virtualFile -> scope),
        debugReason,
        CompilationRequest.compilationDeadline(project),
        id,
        project,
        runDocumentCompiler = false,
        closeRequest = true
      )
      CompilerHighlightingService.get(project).compile(incrementalRequest)
    }

    val versions = documentVersionsFor(originFiles)

    CompilationUtils.prepareCompilation(project, id) {
      CompilationUtils.performCompilation(project, id, versions, delayIndicator = true, refreshVfs = false) { client =>
        val tracer = Tracing(project)
        tracer.begin(
          client.compilationId,
          CompilationRequestPhaseEvent(CompilationKind.Worksheet, virtualFile.getPath, debugReason, id, client.compilationId)
        )
        try {
          WorksheetHighlightingCompiler.compile(file, document, module, client)
        } finally {
          // If the compilation never started (e.g. it was cancelled, or the server failed), no
          // CompilationFinished will arrive to end the span; anything still open here is stranded, so end it.
          // On the normal path it was already handed off and finished, so this is a no-op.
          tracer.mapAndEnd(client.compilationId)(e => Some(e.closed()))
        }
      }
    }
  }

  private def documentVersionsFor(files: Map[VirtualFile, Document]): SerializableMap[CanonicalPath, Long] =
    documentVersions.map { case (_, DocumentVersion(path, version)) =>
      path -> version
    }.to(immutable.HashMap.mapFactory[CanonicalPath, Long])
}