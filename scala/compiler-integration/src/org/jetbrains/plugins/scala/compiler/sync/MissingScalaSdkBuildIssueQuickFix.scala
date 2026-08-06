package org.jetbrains.plugins.scala.compiler.sync

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project

import java.util.concurrent.CompletableFuture

//noinspection ApiStatus,UnstableApiUsage
private final class MissingScalaSdkBuildIssueQuickFix(syncActions: Seq[SyncAction]) extends BuildIssueQuickFix {
  override def getId: String = MissingScalaSdkBuildIssueQuickFix.ID

  override def runQuickFix(project: Project, dataContext: DataContext): CompletableFuture[_] = {
    syncActions.foreach(_.syncProject(project, dataContext))
    CompletableFuture.completedFuture(null)
  }
}

private object MissingScalaSdkBuildIssueQuickFix {
  final val ID = "missing.scala.sdk.build.issue.quick.fix"
}
