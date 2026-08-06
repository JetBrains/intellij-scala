package org.jetbrains.sbt.runner.beforeLaunch.utils

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.task.{ProjectTaskContext, ProjectTaskListener}

import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks invocations of the IntelliJ Build / Make before-launch step.
 *
 * The tracker listens to `ProjectTaskListener` events
 * and counts only project-task runs whose build originator is `CompileStepBeforeRun`.
 *
 * This is the platform boundary where the before-launch Build step has submitted work to `ProjectTaskManager`.
 *
 * NOTE: This intentionally does not assert which backend performs the work.
 * Depending on project shape and settings, the task may later be handled by JPS, sbt-shell build delegation,
 * another project task runner, or a test replacement.
 * The tracked fact is narrower: "the before-launch Build step itself was invoked"
 */
private[runner] final class CompileStepBeforeRunTracker(project: Project, parentDisposable: Disposable) {
  private val startedBuilds = new AtomicInteger()

  private val listener: ProjectTaskListener = new ProjectTaskListener {
    override def started(context: ProjectTaskContext): Unit = {
      // This is the shared positive/negative signal: the Build before-run step started, not any specific build backend.
      if (context.getBuildOriginatorClass == classOf[CompileStepBeforeRun]) {
        startedBuilds.incrementAndGet()
      }
    }
  }
  project.getMessageBus.connect(parentDisposable).subscribe(ProjectTaskListener.TOPIC, listener)

  def startedBuildCount: Int = startedBuilds.get()
}
