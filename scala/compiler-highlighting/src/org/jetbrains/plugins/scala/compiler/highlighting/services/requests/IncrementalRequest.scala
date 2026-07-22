package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bsp.BspUtil
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilationRequestPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.highlighting.services.util.CompilationUtils
import org.jetbrains.plugins.scala.compiler.highlighting.services.{DocumentCompilerAvailabilityService, SaveService}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.{CanonicalPath, DocumentVersion}

import scala.collection.immutable
import scala.concurrent.Promise
import scala.concurrent.duration.Deadline

type SerializableMap[K, V] = Map[K, V] & Serializable

abstract class IncrementalRequest(
  val fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
  val debugReason: String,
  override val deadline: Deadline,
  val requestId: RequestId,
  val project: Project,
  val runDocumentCompiler: Boolean = true,
  val closeRequest: Boolean = false
) extends BaseCompilationRequest(
  fileCompilationScopes.map { case (vf, scope) => vf -> scope.document },
  deadline,
  requestId
) {
  override val priority: Int = 1

  /** Returns a new instance with updated file compilation scopes. */
  def withScopes(scopes: Map[VirtualFile, FileCompilationScope]): IncrementalRequest

  override def isReadyForExecution: RequestState = {
    if (isExpired) return RequestState.Expired

    if (deadline.isOverdue()) {
      if (DumbService.isDumb(project)) return RequestState.NotReady
      canDocumentsBeCompiled
    } else {
      RequestState.NotReady
    }
  }

  override private[services] def execute(): Unit = {
    CompilationUtils.prepareCompilation(project, id) {
      val promise = Promise[Unit]()
      // Documents must be saved on the UI thread, so a thread shift is mandatory in this case.
      invokeLater {
        val future = if (project.isDisposed) scala.concurrent.Future.unit else {
          SaveService(project).saveDocuments(id)
          // Perform the rest of the execution of this incremental compilation on a background thread.
          val docVersions: SerializableMap[CanonicalPath, Long] = documentVersionsFor(fileCompilationScopes)
          CompilationUtils.performCompilation(project, id, docVersions, delayIndicator = false, refreshVfs = true) { client =>
            val requestKey = client.compilationId
            val incrementalFiles = fileCompilationScopes.keys.map(_.getPath).mkString(", ")
            try {
              // we trigger document compilation after incremental so we should keep parent trace open
              Tracing(project).begin(requestKey,
                CompilationRequestPhaseEvent(kind, incrementalFiles, debugReason,
                  id, requestKey, closeParentValue = false, closeOnEndValue = closeRequest))

              doCompile(fileCompilationScopes, client, docVersions)
            } finally {
              // If the compilation was cancelled before its CompilationStarted arrived (e.g. superseded by a
              // newer edit), no CompilationFinished will arrive to end it. Anything still
              // open under this compilationId here is stranded: end it, which self-removes its own context
              // key. On the normal path it was already handed off and finished, so this is a no-op.
              Tracing(project).mapAndEnd(requestKey)(e => Some(e.closed()))
            }
          }
        }
        promise.completeWith(future)
      }
      promise.future
    }
  }

  protected def doCompile(
    scopes: Map[VirtualFile, FileCompilationScope],
    client: CompilerEventGeneratingClient,
    docVersions: SerializableMap[CanonicalPath, Long]
  ): Unit

  private def canDocumentsBeCompiled: RequestState = {
    val documents = originFiles.valuesIterator
    var result: RequestState = RequestState.Ready
    while (documents.hasNext) {
      val document = documents.next()
      canDocumentBeCompiled(project, document) match {
        case RequestState.Expired => return RequestState.Expired
        case RequestState.NotReady => result = RequestState.NotReady
        case RequestState.Ready => // Continue
      }
    }
    result
  }

  protected def mergeSourceScope(scopes: Map[VirtualFile, FileCompilationScope]): SourceScope =
    if (scopes.values.map(_.sourceScope).forall(_ == SourceScope.Production)) SourceScope.Production
    else SourceScope.Test

  protected def enableDocumentCompiler(fileCompilationScopes: Map[VirtualFile, FileCompilationScope]): Unit = {
    fileCompilationScopes.foreach { case (virtualFile, FileCompilationScope(_, _, _, _, psiFile)) =>
      if (psiFile.is[ScalaFile] && !project.isDisposed) {
        DocumentCompilerAvailabilityService(project).enable(virtualFile)
      }
    }
  }

  private def documentVersionsFor(scopes: Map[VirtualFile, FileCompilationScope]): SerializableMap[CanonicalPath, Long] =
    documentVersions.map { case (vf, DocumentVersion(path, version)) =>
      path -> version
    }.to(immutable.HashMap.mapFactory[CanonicalPath, Long])
}
object IncrementalRequest {
  def apply(fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
            debugReason: String,
            deadline: Deadline,
            requestId: RequestId,
            project: Project,
            runDocumentCompiler: Boolean = true,
            closeRequest: Boolean = false
           ): IncrementalRequest = {
    if (BspUtil.isBspProject(project)) {
      BspIncrementalRequest(fileCompilationScopes, debugReason, deadline, requestId, project, runDocumentCompiler, closeRequest)
    } else {
      JpsIncrementalRequest(fileCompilationScopes, debugReason, deadline, requestId, project, runDocumentCompiler, closeRequest)
    }
  }
}
