package org.jetbrains.sbt.shell

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.impl.compiler.{ArtifactCompileScope, ArtifactsWorkspaceSettings}
import com.intellij.task.{ProjectModelBuildTask, ProjectTaskContext, ProjectTaskRunner}
import com.intellij.util.ModalityUiUtil
import org.jetbrains.concurrency.AsyncPromise

import scala.collection.mutable
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.Using

//noinspection UsagesOfObsoleteApi (the API is enforece by the platform)
private[shell] object SbtJpsArtifactPackagingUtil {

  private val Log = Logger.getInstance(this.getClass)

  /**
   * Runs the JPS artifact packaging phase after the delegated sbt build phase.
   *
   * Similar JPS implementation points:
   *  - [[com.intellij.task.impl.JpsProjectTaskRunner.runArtifactsBuildTasks]]
   *  - [[com.intellij.task.impl.JpsProjectTaskRunner.buildArtifacts]]
   *
   * How original JPS sequencing works:
   *  - In [[com.intellij.task.impl.JpsProjectTaskRunner.run]], module/resource/file/artifact task scheduling happens
   *    in one EDT block and one collector lifetime (`try-with-resources`).
   *  - Each `CompilerManager.make(...)` is asynchronous, but all requests are aggregated by a single JPS notification
   *    collector and effectively serialized by compiler infrastructure.
   *
   * Why this hybrid path needs explicit chaining:
   *  - sbt compilation here is a separate async phase (`sbtBuildPromise`) running outside JPS task scheduling.
   *  - JPS artifact packaging cannot be scheduled together with sbt compile in one JPS collector scope because sbt
   *    completion is not a JPS callback.
   *  - Therefore we first await sbt completion and only then schedule JPS artifact packaging.
   *
   * Differences from JPS implementation:
   *  - Conceptually similar ordering (build + artifact packaging in one "run"), but with a different prerequisite:
   *    here artifact packaging is explicitly gated by successful delegated sbt build completion.
   *    In JPS runner there is no sbt phase; module/resource/file/artifact JPS tasks are scheduled within the same JPS run.
   *  - JPS-style aggregation is implemented via local helper classes in [[SbtJpsBuildNotifications]]
   *    because JPS collector/notification classes are private in [[com.intellij.task.impl.JpsProjectTaskRunner]].
   *  - Unlike JPS runner, this hybrid path does not aggregate `JPS_BUILD_DATA` / `CompileContext` compatibility data;
   *    only final `isAborted` / `hasErrors` result state is collected.
   */
  def chainSbtBuildAndJpsArtifactPackaging(
    project: Project,
    context: ProjectTaskContext,
    sbtBuildPromise: AsyncPromise[ProjectTaskRunner.Result],
    artifactBuildTasks: Seq[(ProjectModelBuildTask[?], Artifact)]
  ): AsyncPromise[ProjectTaskRunner.Result] = {
    val promiseResult = new AsyncPromise[ProjectTaskRunner.Result]()

    sbtBuildPromise.onSuccess { sbtBuildResult =>
      if (sbtBuildResult.isAborted || sbtBuildResult.hasErrors) {
        Log.debug(s"Skipping JPS artifact packaging because delegated sbt build finished with aborted=${sbtBuildResult.isAborted}, errors=${sbtBuildResult.hasErrors}")
        promiseResult.setResult(sbtBuildResult)
      }
      else {
        Log.debug(s"Starting JPS artifact packaging for ${artifactBuildTasks.size} artifact task(s)")

        // Run on EDT similar to how it's done in `com.intellij.task.impl.JpsProjectTaskRunner.run`.
        // Method `com.intellij.openapi.compiler.CompilerManager.make` and maybe some other calls require EDT for some reason.
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState, project.getDisposed, () => {
          val jpsArtifactBuildPromise = runJpsArtifactPackaging(project, context, artifactBuildTasks)
          jpsArtifactBuildPromise.onSuccess(promiseResult.setResult)
          jpsArtifactBuildPromise.onError(promiseResult.setError)
        })
      }
    }
    sbtBuildPromise.onError(promiseResult.setError)

    promiseResult
  }

  private def runJpsArtifactPackaging(
    project: Project,
    context: ProjectTaskContext,
    artifactBuildTasks: Seq[(ProjectModelBuildTask[?], Artifact)]
  ): AsyncPromise[ProjectTaskRunner.Result] = {
    // `ProjectModelBuildTask.isIncrementalBuild` defines packaging mode:
    //  - incremental (`true`)  -> normal "make artifact" (up-to-date checks are allowed)
    //  - non-incremental (`false`) -> "rebuild artifact" (force full packaging)
    val makeArtifacts = mutable.LinkedHashSet.empty[Artifact]
    val rebuildArtifacts = mutable.LinkedHashSet.empty[Artifact]

    artifactBuildTasks.foreach { case (task, artifact) =>
      if (task.isIncrementalBuild) makeArtifacts += artifact
      else rebuildArtifacts += artifact
    }

    val makeArtifactsBatch = makeArtifacts.toSeq
    val rebuildArtifactsBatch = rebuildArtifacts.toSeq
    val promiseResult = new AsyncPromise[ProjectTaskRunner.Result]()
    val notificationCollector = new SbtJpsBuildNotifications.MyNotificationCollector(Log, promiseResult)
    Log.debug(s"Prepared JPS artifact packaging batches: make=${makeArtifacts.size}, rebuild=${rebuildArtifacts.size}")

    // This flow emulates JPS `runArtifactsBuildTasks` + two `buildArtifacts` calls in `com.intellij.task.impl.JpsProjectTaskRunner`.
    // We cannot reuse these internals directly because they are private in JPS runner.
    // Collector lifecycle is intentionally aligned with JPS usage:
    // schedule all batches and close collector in finally (like try-with-resources in Java).
    Using.resource(notificationCollector) { _ =>
      runArtifactBatch(project, context, makeArtifactsBatch, forceArtifactBuild = false, notificationCollector)
      runArtifactBatch(project, context, rebuildArtifactsBatch, forceArtifactBuild = true, notificationCollector)
    }

    promiseResult
  }

  private def runArtifactBatch(
    project: Project,
    context: ProjectTaskContext,
    artifacts: Seq[Artifact],
    forceArtifactBuild: Boolean,
    notificationCollector: SbtJpsBuildNotifications.MyNotificationCollector
  ): Unit = {
    // This method emulates JPS `buildArtifacts` for one explicit batch passed as an argument.
    // Similar places in JPS:
    //  - `com.intellij.task.impl.JpsProjectTaskRunner.buildArtifacts`
    //    (creates `ArtifactCompileScope`, sets `ArtifactsWorkspaceSettings`, invokes `CompilerManager.make`)
    // JPS uses private `MyNotificationCollector` to aggregate multiple async calls. We use a local equivalent.
    if (artifacts.isEmpty) {
      return
    }

    Log.debug(s"Running JPS artifact batch: artifacts=${artifacts.size}, forceArtifactBuild=$forceArtifactBuild")
    val compilerManager = CompilerManager.getInstance(project)
    // IMPORTANT:
    // Use an artifact-only scope on top of an empty module scope.
    // In this hybrid flow, module compilation is already delegated to sbt shell.
    // We only need JPS artifact builders for packaging.
    // If we use `createArtifactsScope(project, artifacts, ...)` directly, it includes modules from artifact layout
    // and `CompilerManager.make(...)` may invoke JPS module compilation checks/builders (including Scala/JPS path).
    // That leads to unexpected JPS compilation in "use sbt shell for build" mode.
    val emptyModuleScope = compilerManager.createModulesCompileScope(Array.empty, false, false, false)
    val scope = ArtifactCompileScope.createScopeWithArtifacts(emptyModuleScope, artifacts.asJava, forceArtifactBuild)
    ArtifactsWorkspaceSettings.getInstance(project).setArtifactsToBuild(artifacts.asJava)
    ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.set(scope, context.getSessionId)

    val notification = new SbtJpsBuildNotifications.MyCompileStatusNotification(notificationCollector)
    compilerManager.make(scope, notification)
  }
}
