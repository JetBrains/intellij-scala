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

case class BspIncrementalRequest(
  override val fileCompilationScopes: Map[VirtualFile, FileCompilationScope],
  override val requestId: RequestId,
  override val project: Project,
  override val runDocumentCompiler: Boolean = true,
  override val closeRequest: Boolean = false
) extends IncrementalRequest(fileCompilationScopes, requestId, project, runDocumentCompiler, closeRequest):

  override def kind: CompilationKind = CompilationKind.BSPIncremental

  override protected def doCompile(
    scopes: Map[VirtualFile, FileCompilationScope],
    client: CompilerEventGeneratingClient,
    scopeDocumentVersions: SerializableMap[CanonicalPath, Long]
  ): Unit = 
    val context = new ProjectTaskContext()
    val modules = scopes.values.map(_.module.findRepresentativeModuleForSharedSourceModuleOrSelf).toSet.toArray
    val sourceScope = mergeSourceScope(scopes)
    val tasks = modules.map { module =>
      ProjectTaskManager.getInstance(project)
        .createModulesBuildTask(Array(module), true, true, false, sourceScope == SourceScope.Test)
    }
    val reporter = new CompilerEventReporter(project, client.compilationId)
    val arguments = CustomTaskArguments(CompilerHighlightingBundle.message("highlighting.compilation"), reporter)
    val taskRunner = new BspProjectTaskRunner(Some(arguments))
    val token = compilationStarted(scopes)
    val promise = taskRunner.run(project, context, tasks.toSeq*)
    val result = promise.blockingGet(1, TimeUnit.DAYS)
    val covered = coveredFiles(reporter.coveredFiles)
    if ((result eq null) || (result.hasErrors && reporter.successful && client.successful)) {
      // The runner failed with nothing reported against the sources, e.g. no target was found for the modules:
      // that says nothing about whether they build, so nothing is recorded.
      if (Log.isDebugEnabled) Log.debug(s"BSP build of modules ${modules.map(_.getName).mkString(", ")} did not run")
    } else if (reporter.successful && client.successful) {
      compilationSucceeded(token, scopes, covered)
    } else {
      compilationFailed(token, scopes, covered, coveredFiles(reporter.erroredFiles))
    }

    if (!DocumentUtil.stillValid(scopeDocumentVersions)) {
      Tracing(project).instant(EndEvent(id, "Documents changed during BSP incremental compilation"))
      return
    }

    if(runDocumentCompiler && reporter.successful) {
      DocumentCompilationTrigger.triggerDocumentCompilationInAllOpenEditors(project,
        "After BSP compilation",
        id,
        Some(client)
      )
    }

  /** Returns a new instance with updated file compilation scopes. */
  override def withScopes(scopes: Map[VirtualFile, FileCompilationScope]): IncrementalRequest = copy(fileCompilationScopes = scopes)
