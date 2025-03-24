package org.jetbrains.sbt.project.execution

import com.intellij.build.events.impl.{FinishEventImpl, SkippedResultImpl, StartEventImpl, SuccessResultImpl}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTask, ExternalSystemTaskId, ExternalSystemTaskNotificationListener, ExternalSystemTaskState}
import com.intellij.openapi.externalSystem.service.execution.{ExternalSystemExecutionAware, ExternalSystemJdkException, ExternalSystemJdkUtil, ExternalSystemJdkUtilKt}
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.notification.callback.OpenProjectJdkSettingsCallback
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings

class SbtExecutionAware extends ExternalSystemExecutionAware {

  private val log = Logger.getInstance(getClass)

  private object DefaultSdkLookupId extends SdkLookupProvider.Id

  override def prepareExecution(
    externalSystemTask: ExternalSystemTask,
    externalProjectPath: String,
    isPreviewMode: Boolean,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    project: Project
  ): Unit = {
    if (isPreviewMode) return

    val settings = SbtSettings.getInstance(project)
    val projectSettings = settings.getLinkedProjectSettings(externalProjectPath)
    //NOTE: if the custom VM path is set in the sbt setting, then the import can happen no matter if any JDK is being downloaded
    if (projectSettings == null || settings.customVMPath != null)
      return

    // TODO
    //  1. If a project SDK or the JDK specified in the project settings is being downloaded, this execution-aware
    //  ensures that the sbt import will wait for the download to complete. In the previous implementation,
    //  the import sometimes started successfully due to fallback JDK resolution logic in `SbtExternalSystemManager.getVmExecutable`.
    //  To be completely correct, we might need to implement similar fallback behavior here (or simply move that logic here), so that if a fallback JDK exists,
    //  the import does not wait for the download to finish.
    //  2. After resolution we can verify if the JDK is correct - it could be useful because, currently,
    //  if the SDK is resolved but broken, no meaningful message is displayed.
    resolveJdk(project, projectSettings, externalSystemTask, taskNotificationListener)
  }

  private def resolveJdk(
    project: Project,
    projectSettings: SbtProjectSettings,
    task: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
  ): Unit = {
    val provider = SdkLookupProvider.getInstance(project, DefaultSdkLookupId)
    val sdkInfo = nonblockingResolveJdk(provider, project, projectSettings.jdkName)
    val isResolved = sdkInfo.is[SdkInfo.Resolved]
    if (!isResolved) {
      waitForJvmResolving(provider, task, taskNotificationListener)
    }
  }

  private def waitForJvmResolving(
    lookupProvider: SdkLookupProvider,
    task: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener
  ): Unit = {
    if (ApplicationManager.getApplication.isDispatchThread) {
      log.error("Do not perform synchronous wait for sdk downloading in EDT - causes deadlock.")
      val exceptionMessage = SbtBundle.message("sbt.execution.exception", OpenProjectJdkSettingsCallback.ID)
      throw new ExternalSystemJdkException(exceptionMessage, null, OpenProjectJdkSettingsCallback.ID)
    }

    val progressIndicator = Option(lookupProvider.getProgressIndicator).getOrElse(new ProgressIndicatorBase())

    submitProgressStarted(task, taskNotificationListener, progressIndicator, progressIndicator)
    whenTaskCanceled(task, progressIndicator.cancel())

    lookupProvider.waitForLookup()

    submitProgressFinished(task, taskNotificationListener, progressIndicator, progressIndicator)
  }

  private def whenTaskCanceled(task: ExternalSystemTask, onCancelAction: => Unit): Unit = {
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    val notificationListener = new ExternalSystemTaskNotificationListener {
      override def onCancel(projectPath: String, id: ExternalSystemTaskId): Unit = onCancelAction
    }
    progressManager.addNotificationListener(task.getId, notificationListener)
    if (task.getState == ExternalSystemTaskState.CANCELED || task.getState == ExternalSystemTaskState.CANCELING) {
      onCancelAction
    }
  }

  private def submitProgressStarted(
    task: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    progressIndicator: ProgressIndicator,
    eventId: ProgressIndicator
  ): Unit = {
    val message = Option(progressIndicator.getText).getOrElse(SbtBundle.message("sbt.execution.jdk.being.resolved"))
    val buildEvent = new StartEventImpl(eventId, task.getId, System.currentTimeMillis(), message)
    val notificationEvent = new ExternalSystemBuildEvent(task.getId, buildEvent)
    taskNotificationListener.onStatusChange(notificationEvent)
  }

  private def submitProgressFinished(
    task: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    progressIndicator: ProgressIndicator,
    eventId: Any
  ): Unit = {
    val result =
      if (progressIndicator.isCanceled) new SkippedResultImpl()
      else new SuccessResultImpl()

    val message = Option(progressIndicator.getText).getOrElse(SbtBundle.message("sbt.execution.jdk.has.been.resolved"))
    val buildEvent = new FinishEventImpl(eventId, task.getId, System.currentTimeMillis(), message, result)
    val notificationEvent = new ExternalSystemBuildEvent(task.getId, buildEvent)
    taskNotificationListener.onStatusChange(notificationEvent)
  }

  private def nonblockingResolveJdk(
    provider: SdkLookupProvider,
    project: Project,
    projectSettingsJdkName: Option[String]
  ): SdkInfo = {
    val jdkReference = projectSettingsJdkName match {
      case Some(value) => value
      case None => ExternalSystemJdkUtil.USE_PROJECT_JDK
    }

    val projectSdk = ProjectRootManager.getInstance(project).getProjectSdk
    ExternalSystemJdkUtilKt.nonblockingResolveJdkInfo(provider, projectSdk, jdkReference)
  }
}
