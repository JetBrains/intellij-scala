package org.jetbrains.plugins.scala.compiler.sync

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.compiler.progress.BuildIssueContributor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.scala.compiler.MissingScalaSdk

import scala.jdk.CollectionConverters._

//noinspection ApiStatus,UnstableApiUsage
private final class MissingScalaSdkBuildIssueContributor extends BuildIssueContributor {
  override def createBuildIssue(
    project: Project,
    moduleNames: java.util.Collection[String],
    title: String,
    message: String,
    kind: MessageEvent.Kind,
    virtualFile: VirtualFile,
    navigatable: Navigatable
  ): BuildIssue = {
    if (!isMissingScalaSdkMessage(message, kind)) return null

    val handlers = BuildToolProjectSyncHelper.projectSyncHandlers(project)
    val (externalSystemHandlers, customHandlers) = handlers.partitionMap {
      case ProjectSyncHandler.ExternalSystem => Left(ProjectSyncHandler.ExternalSystem)
      case ProjectSyncHandler.Custom(action) => Right(ProjectSyncHandler.Custom(action))
    }

    val externalSystemSyncAction = if (externalSystemHandlers.nonEmpty) Some(ExternalSystemSyncAction) else None
    val customSyncActions = customHandlers.map(_.syncAction)

    val syncActions = externalSystemSyncAction.toSeq ++ customSyncActions
    val quickFix =
      if (syncActions.nonEmpty) Some(new MissingScalaSdkBuildIssueQuickFix(syncActions))
      else None

    new MissingScalaSdkBuildIssue(moduleNames.asScala.toSeq, quickFix)
  }

  private def isMissingScalaSdkMessage(message: String, kind: MessageEvent.Kind): Boolean =
    kind == MessageEvent.Kind.WARNING &&
      message.startsWith(MissingScalaSdk.MessagePrefix) &&
      message.contains(MissingScalaSdk.SkippedScalaSourcesMessagePrefix)
}
