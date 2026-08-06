//noinspection ApiStatus,UnstableApiUsage
package org.jetbrains.sbt.project.execution

import com.intellij.build.events.BuildEvents
import com.intellij.build.events.impl.{SkippedResultImpl, SuccessResultImpl}
import com.intellij.build.issue.{BuildIssue, BuildIssueQuickFix}
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTask, ExternalSystemTaskId, ExternalSystemTaskNotificationListener, ExternalSystemTaskState}
import com.intellij.openapi.externalSystem.service.execution.{ExternalSystemExecutionAware, ExternalSystemJdkException, ExternalSystemJdkUtil, ExternalSystemJdkUtilKt}
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.notification.callback.OpenProjectJdkSettingsCallback
import com.intellij.openapi.progress.CoroutinesKt.runBlockingCancellable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import com.intellij.openapi.roots.ui.configuration.{ProjectSettingsService, SdkLookupProvider}
import com.intellij.pom.Navigatable
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.ExternalSystemNotificationReporter
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.sbt.project.execution.SbtExecutionAware.{OpenProjectJDKSettingsQuickFix, OpenProjectJDKSettingsQuickFixID}
import org.jetbrains.sbt.project.template.wizard.JdkSbtCompatibilityChecker
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.{SbtBundle, SbtVersionDetector}

import java.util
import java.util.concurrent.CompletableFuture

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
    val sdkInfo = resolveJdk(project, externalSystemTask, taskNotificationListener)
    validateJDKWithSbt(sdkInfo, externalSystemTask, taskNotificationListener, externalProjectPath, project)
  }

  private def validateJDKWithSbt(
    sdk: SdkInfo,
    externalSystemTask: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    externalProjectPath: String,
    project: Project
  ): Unit = {
    sdk match {
      case resolved: SdkInfo.Resolved =>
        val versionString = Option(resolved.getVersionString).map(JavaVersion.parse)

        versionString.foreach { version =>
          val sbtVersion = SbtVersionDetector.detectSbtVersion(project, externalProjectPath)
          val minimumRequiredJdk = JdkSbtCompatibilityChecker.getMinimumJdkToSbtCompatibleVersion(version, sbtVersion)
          minimumRequiredJdk match {
            case Some(minJdk) =>
              val description = SbtBundle.message("sbt.jdk.compatibility.issue.jdk.too.low.description", sbtVersion.value.presentation, minJdk.feature)
              displayJDKSbtCompatibilityWarning(externalSystemTask, taskNotificationListener, externalProjectPath, description)
            case None =>
              val highestCompatibleJdk = JdkSbtCompatibilityChecker.getHighestCompatibleJdkForSbt(version, sbtVersion)
              highestCompatibleJdk.foreach { javaVersion =>
                val description =  SbtBundle.message("sbt.jdk.compatibility.issue.description", javaVersion.feature, sbtVersion.value.presentation)
                displayJDKSbtCompatibilityWarning(externalSystemTask, taskNotificationListener, externalProjectPath, description)
              }
          }
        }
      case _ =>
    }
  }

  private def resolveJdk(
    project: Project,
    task: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
  ): SdkInfo = {
    val provider = SdkLookupProvider.getInstance(project, DefaultSdkLookupId)
    val sdkInfo = nonblockingResolveJdk(provider, project)
    val isResolving = sdkInfo.is[SdkInfo.Resolving]
    if (isResolving) {
      waitForJvmResolving(provider, task, taskNotificationListener)
    }
    nonblockingResolveJdk(provider, project)
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
      // `ExternalSystemTaskNotificationListener` contains two default methods that call each other if there is an
      // implementation of a specific method in the subclass. Reflection is used for this purpose.
      // It's causing issues because Scala compiles Java interface default methods into concrete method implementations in subclasses.
      // As a result, reflection shows that both methods exist in the subclass,
      // causing the two `onTaskOutput` methods to call each other indefinitely, leading to a `StackOverflowError`.
      // An empty implementation of `onTaskOutput` prevents this.
      override def onTaskOutput(id: ExternalSystemTaskId, text: String, outputType: ProcessOutputType): Unit = { }

      override def onCancel(projectPath: String, id: ExternalSystemTaskId): Unit = onCancelAction
    }
    progressManager.addNotificationListener(task.getId, notificationListener)
    if (task.getState == ExternalSystemTaskState.CANCELED || task.getState == ExternalSystemTaskState.CANCELING) {
      onCancelAction
    }
  }

  private def displayJDKSbtCompatibilityWarning(
    task: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    externalProjectPath: String,
    @Nls description: String
  ): Unit = {
    val esReporter = new ExternalSystemNotificationReporter(externalProjectPath, task.getId, taskNotificationListener)
    val buildIssue = new BuildIssue {
      override def getTitle: String = SbtBundle.message("sbt.jdk.compatibility.issue.title")

      override def getDescription: String =
        description + SbtBundle.message("sbt.jdk.compatibility.issue.open.settings", OpenProjectJDKSettingsQuickFixID)

      override def getQuickFixes: util.List[BuildIssueQuickFix] = java.util.List.of(OpenProjectJDKSettingsQuickFix)

      override def getNavigatable(project: Project): Navigatable = null
    }
    esReporter.warning(buildIssue)
  }

  private def submitProgressStarted(
    task: ExternalSystemTask,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    progressIndicator: ProgressIndicator,
    eventId: ProgressIndicator
  ): Unit = {
    val message = Option(progressIndicator.getText).getOrElse(SbtBundle.message("sbt.execution.jdk.being.resolved"))
    val buildEvent = BuildEvents.getInstance()
      .start(eventId, message)
      .withParentId(task.getId)
      .withTime(System.currentTimeMillis())
      .build()
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
    val buildEvent = BuildEvents.getInstance()
      .finish(eventId, message, result)
      .withParentId(task.getId)
      .withTime(System.currentTimeMillis())
      .build()
    val notificationEvent = new ExternalSystemBuildEvent(task.getId, buildEvent)
    taskNotificationListener.onStatusChange(notificationEvent)
  }

  private def nonblockingResolveJdk(
    provider: SdkLookupProvider,
    project: Project
  ): SdkInfo = {
    val projectSdk = ProjectRootManager.getInstance(project).getProjectSdk
    runBlockingCancellable { (_, continuation) =>
      ExternalSystemJdkUtilKt.resolveJdkInfo(provider, project, projectSdk, ExternalSystemJdkUtil.USE_PROJECT_JDK, continuation)
    }
  }
}

object SbtExecutionAware {
  private val OpenProjectJDKSettingsQuickFixID = "open_project_JDK"

  private val OpenProjectJDKSettingsQuickFix = new BuildIssueQuickFix {
    override def getId: String = OpenProjectJDKSettingsQuickFixID

    override def runQuickFix(project: Project, dataContext: DataContext): CompletableFuture[?] = {
      ProjectSettingsService.getInstance(project).openProjectSettings()
      CompletableFuture.completedFuture(null)
    }
  }
}
