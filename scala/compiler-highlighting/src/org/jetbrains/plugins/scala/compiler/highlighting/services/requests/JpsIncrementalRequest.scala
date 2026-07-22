package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.highlighting.compilers.IncrementalCompiler
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.DocumentCompilationTrigger
import org.jetbrains.plugins.scala.compiler.highlighting.util.DocumentUtil
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.CanonicalPath

import scala.concurrent.duration.Deadline

case class JpsIncrementalRequest(
  override val fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
  override val debugReason: String,
  override val deadline: Deadline,
  override val requestId: RequestId,
  override val project: Project,
  override val runDocumentCompiler: Boolean = true,
  override val closeRequest: Boolean = false
) extends IncrementalRequest(fileCompilationScopes,
  debugReason, deadline, requestId, project, runDocumentCompiler, closeRequest) {

  override def delayed(newDeadline: Deadline): JpsIncrementalRequest = copy(deadline = newDeadline)

  override val kind: CompilationKind = CompilationKind.JPSIncremental

  override protected def doCompile(
    scopes: Map[VirtualFile, FileCompilationScope],
    client: CompilerEventGeneratingClient,
    docVersions: SerializableMap[CanonicalPath, Long]
  ): Unit = {
    val modules = scopes.values.map(_.module.findRepresentativeModuleForSharedSourceModuleOrSelf).toSet
    val sourceScope = mergeSourceScope(scopes)
    IncrementalCompiler.compile(project, modules, sourceScope, client)

    if (!DocumentUtil.stillValid(docVersions)) {
      Tracing(project).instant(EndEvent(id, "Documents changed during JPS incremental compilation"))
      return
    }

    val handedOff = runDocumentCompiler && client.successful &&
      DocumentCompilationTrigger.triggerDocumentCompilationInAllOpenEditors(project, "After JPS compilation",
        id,
        Some(client)
      )
    if (!handedOff) {
      Tracing(project).instant(EndEvent(id, "No document compilation followed JPS incremental compilation"))
    }
    if (client.successful) {
      enableDocumentCompiler(scopes)
    }
  }

  /** Returns a new instance with updated file compilation scopes. */
  override def withScopes(scopes: Map[VirtualFile, FileCompilationScope]): IncrementalRequest =
    copy(fileCompilationScopes = scopes)
}

