package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bsp.BspUtil
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilationRequestPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.{CompilationToken, ModuleKey}
import org.jetbrains.plugins.scala.compiler.highlighting.services.util.CompilationUtils
import org.jetbrains.plugins.scala.compiler.highlighting.services.{CompilationLogicService, SaveService}
import org.jetbrains.plugins.scala.compiler.highlighting.util.TracingUtil.endTrace
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.util.{CanonicalPath, DocumentVersion}

import scala.collection.immutable
import scala.concurrent.Promise

type SerializableMap[K, V] = Map[K, V] & Serializable

abstract class IncrementalRequest(
  val fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
  val requestId: RequestId,
  val project: Project,
  val runDocumentCompiler: Boolean = true,
  val closeRequest: Boolean = false
) extends BaseCompilationRequest(
  project,
  fileCompilationScopes.map { case (vf, scope) => vf -> scope.document },
  requestId
) {
  protected val logic = CompilationLogicService(project)
  /** Returns a new instance with updated file compilation scopes. */
  def withScopes(scopes: Map[VirtualFile, FileCompilationScope]): IncrementalRequest
  
  override private[services] def execute(): Unit = {
    CompilationUtils.prepareCompilation(project, id, true) {
      val promise = Promise[Unit]()
      // Documents must be saved on the UI thread, so a thread shift is mandatory in this case.
      invokeLater(ModalityState.nonModal()) {
        val future = if (project.isDisposed) scala.concurrent.Future.unit else {
          SaveService(project).saveDocuments(id)
          // Perform the rest of the execution of this incremental compilation on a background thread.
          val docVersions: SerializableMap[CanonicalPath, Long] = documentVersionsFor(fileCompilationScopes)
          CompilationUtils.performCompilation(project, id, docVersions, delayIndicator = false, refreshVfs = true) { client =>
            val compilationId = client.compilationId
            val incrementalFiles = fileCompilationScopes.keys.map(_.getPath).mkString(", ")
            var threw = true
            try {
              // we trigger document compilation after incremental so we should keep parent trace open
              Tracing(project).begin(compilationId,
                CompilationRequestPhaseEvent(kind, incrementalFiles, "",
                  id, compilationId, closeParentValue = false, closeOnEndValue = closeRequest))

              doCompile(fileCompilationScopes, client, docVersions)
              threw = false
            } finally {
              if (threw) {
                // If the compilation was cancelled before its CompilationStarted arrived (e.g. superseded by a
                // newer edit), no CompilationFinished will arrive to end it. Anything still
                // open under this compilationId here is stranded: end it, which self-removes its own context
                // key. On the normal path it was already handed off and finished, so this is a no-op.
                Tracing(project).mapAndEnd(compilationId)(e => Some(e.closed("Compilation failed")))
                Tracing(project).endTrace(requestId, "Compilation finished")
              }
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
  

  protected def mergeSourceScope(scopes: Map[VirtualFile, FileCompilationScope]): SourceScope =
    if (scopes.values.map(_.sourceScope).forall(_ == SourceScope.Production)) SourceScope.Production
    else SourceScope.Test

  /** Snapshots the compilation state this request is about to run against; see [[compilationSucceeded]]. */
  protected def compilationStarted(scopes: Map[VirtualFile, FileCompilationScope]): CompilationToken =
    logic.compilationStarted(scopes.keySet)

  /**
   * Records a compilation the compiler reported no errors for, against the state it saw rather than the
   * current one, so that edits made while it ran are not absorbed by its success.
   *
   * Only call this when the compiler reported no errors. A failure — and a compilation that throws, a dead
   * compile server or a cancellation — is recorded by not calling it: `compiledAt` stays where it was, which
   * is what keeps the document compiler away from a target whose output no longer matches its sources.
   *
   * The targets marked are each file's own, not the request's merged source scope: a request mixing a
   * production file with a test one goes out as a test build for both, and recording that would leave the
   * production target of the first unmarked even though it was built.
   */
  protected def compilationSucceeded(token: CompilationToken,
                                     scopes: Map[VirtualFile, FileCompilationScope],
                                     covered: Set[VirtualFile]): Unit =
    if (Log.isDebugEnabled) Log.debug(s"Compilation succeeded for modules:${scopes.map(_._2.module.getName)}")
    logic.compilationSucceeded(token, scopes.values.map(ModuleKey.of).toSet, covered)

  /**
   * Records that the compiler reported errors, so this request's scope stops being carried into later ones.
   *
   * Only for errors the compiler reported. A compilation that throws — a dead compile server, a cancellation
   * — says nothing about whether the sources build, and marking its scope would stop retrying files that were
   * never attempted.
   */
  protected def compilationFailed(token: CompilationToken, scopes: Map[VirtualFile, FileCompilationScope], covered: Set[VirtualFile]): Unit =
    if (Log.isDebugEnabled) Log.debug(s"Compilation failed for files: ${covered.map(_.getPresentableName)}")
    logic.compilationFailed(token, scopes.values.map(ModuleKey.of).toSet, covered)

  /**
   * The sources a compilation reported compiling, as virtual files.
   *
   * A path that resolves to nothing is dropped: it names something outside the VFS, and a file the decision
   * cannot be asked about is one it cannot answer from coverage either.
   */
  protected def coveredFiles(paths: Set[java.nio.file.Path]): Set[VirtualFile] =
    paths.flatMap(_.toVirtualFile).filter(_.isValid)

  private def documentVersionsFor(scopes: Map[VirtualFile, FileCompilationScope]): SerializableMap[CanonicalPath, Long] =
    documentVersions.map { case (vf, DocumentVersion(path, version)) =>
      path -> version
    }.to(immutable.HashMap.mapFactory[CanonicalPath, Long])
}
object IncrementalRequest {
  def apply(fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
            requestId: RequestId,
            project: Project,
            runDocumentCompiler: Boolean = true,
            closeRequest: Boolean = false
           ): IncrementalRequest = {
    if (BspUtil.isBspProject(project)) {
      BspIncrementalRequest(fileCompilationScopes, requestId, project, runDocumentCompiler, closeRequest)
    } else {
      JpsIncrementalRequest(fileCompilationScopes, requestId, project, runDocumentCompiler, closeRequest)
    }
  }
}
