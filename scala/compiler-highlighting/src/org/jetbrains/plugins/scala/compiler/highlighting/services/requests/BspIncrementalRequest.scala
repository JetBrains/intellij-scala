package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.task.{ProjectTaskContext, ProjectTaskManager}
import org.jetbrains.bsp.project.{BspProjectTaskRunner, CustomTaskArguments}
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.build.CompilerEventReporter
import org.jetbrains.plugins.scala.compiler.highlighting.core.{CompilerEventGeneratingClient, FileCompilationScope}
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.triggers.DocumentCompilationTrigger
import org.jetbrains.plugins.scala.compiler.highlighting.util.{CompilerHighlightingBundle, DocumentUtil}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.CanonicalPath

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Deadline

case class BspIncrementalRequest(
  override val fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
  override val debugReason: String,
  override val deadline: Deadline,
  override val requestId: RequestId,
  override val project: Project,
  override val runDocumentCompiler: Boolean = true,
  override val closeRequest: Boolean = false
) extends IncrementalRequest(fileCompilationScopes, debugReason, deadline, requestId, project, runDocumentCompiler, closeRequest) {

  override def kind: CompilationKind = CompilationKind.BSPIncremental

  override protected def doCompile(
    scopes: Map[VirtualFile, FileCompilationScope],
    client: CompilerEventGeneratingClient,
    docVersions: SerializableMap[CanonicalPath, Long]
  ): Unit = {
    val context = new ProjectTaskContext()
    val modules = scopes.values.map(_.module.findRepresentativeModuleForSharedSourceModuleOrSelf).toSet.toArray
    val sourceScope = mergeSourceScope(scopes)
    val task = ProjectTaskManager.getInstance(project)
      .createModulesBuildTask(modules, true, true, false, sourceScope == SourceScope.Test)
    val reporter = new CompilerEventReporter(project, client.compilationId)
    val arguments = CustomTaskArguments(CompilerHighlightingBundle.message("highlighting.compilation"), reporter)
    val taskRunner = new BspProjectTaskRunner(Some(arguments))
    val promise = taskRunner.run(project, context, task)
    promise.blockingGet(1, TimeUnit.DAYS)

    if (!DocumentUtil.stillValid(docVersions)) {
      Tracing(project).instant(EndEvent(id, "Documents changed during BSP incremental compilation"))
      return
    }

    val handedOff = runDocumentCompiler && reporter.successful &&
      DocumentCompilationTrigger.triggerDocumentCompilationInAllOpenEditors(project,
        "After BSP compilation",
        id,
        Some(client)
      )
    if (!handedOff) {
      Tracing(project).instant(EndEvent(id, "No document compilation followed BSP incremental compilation"))
    }
    if (reporter.successful && client.successful) {
      enableDocumentCompiler(scopes)
    }
  }

  /** Returns a new instance with updated file compilation scopes. */
  override def withScopes(scopes: Map[VirtualFile, FileCompilationScope]): IncrementalRequest = copy(fileCompilationScopes = scopes)
  override def delayed(newDeadline: Deadline): BspIncrementalRequest = copy(deadline = newDeadline)

}
