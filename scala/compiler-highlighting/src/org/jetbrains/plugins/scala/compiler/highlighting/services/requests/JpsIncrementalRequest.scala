package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.compilers.IncrementalCompiler
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.DocumentCompilationTrigger
import org.jetbrains.plugins.scala.compiler.highlighting.util.DocumentUtil
import org.jetbrains.plugins.scala.compiler.highlighting.util.TracingUtil.endTrace
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.CanonicalPath

case class JpsIncrementalRequest(
  override val fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
  override val requestId: RequestId,
  override val project: Project,
  override val runDocumentCompiler: Boolean = true,
  override val closeRequest: Boolean = false
) extends IncrementalRequest(fileCompilationScopes, requestId, project, runDocumentCompiler, closeRequest):
  
  override val kind: CompilationKind = CompilationKind.JPSIncremental

  override protected def doCompile(
    scopes: Map[VirtualFile, FileCompilationScope],
    client: CompilerEventGeneratingClient,
    scopeDocumentVersions: SerializableMap[CanonicalPath, Long]
  ): Unit = 
    val modules = scopes.values.map(_.module.findRepresentativeModuleForSharedSourceModuleOrSelf).toSet
    val sourceScope = mergeSourceScope(scopes)
    val token = compilationStarted(scopes)
    IncrementalCompiler.compile(project, modules, sourceScope, client)
    // A cancelled compilation returns normally, without its end. It is neither a success nor a failure, and
    // nothing is dispatched after it: throwing also aborts a worksheet waiting on this as its first build.
    if (client.endIfInterrupted()) throw new ProcessCanceledException()

    val covered = coveredFiles(client.compiledSources)
    if (client.successful) {
      compilationSucceeded(token, scopes, covered)
    } else {
      compilationFailed(token, scopes, covered, coveredFiles(client.erroredSources))
    }

    if (!DocumentUtil.stillValid(scopeDocumentVersions)) {
      Tracing(project).endTrace(id, "Documents changed during JPS incremental compilation")
      return
    }

    if (runDocumentCompiler && client.successful) {
          DocumentCompilationTrigger.triggerDocumentCompilationInAllOpenEditors(project, "After JPS compilation", id, Some(client))
    }
  
  /** Returns a new instance with updated file compilation scopes. */
  override def withScopes(scopes: Map[VirtualFile, FileCompilationScope]): IncrementalRequest =
    copy(fileCompilationScopes = scopes)


