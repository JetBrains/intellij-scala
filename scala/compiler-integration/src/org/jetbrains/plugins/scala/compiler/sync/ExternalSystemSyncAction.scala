package org.jetbrains.plugins.scala.compiler.sync

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.{ActionUiKind, AnActionEvent, DataContext}
import com.intellij.openapi.externalSystem.action.RefreshAllExternalProjectsAction
import com.intellij.openapi.project.Project

import scala.annotation.nowarn

private object ExternalSystemSyncAction extends SyncAction {
  override def syncProject(project: Project, dataContext: DataContext): Unit = {
    //noinspection ApiStatus,UnstableApiUsage
    val refreshAction = new RefreshAllExternalProjectsAction()
    val event = AnActionEvent.createEvent(refreshAction, dataContext, null, "", ActionUiKind.NONE, null)
    ActionUtil.invokeAction(refreshAction, event, null): @nowarn("cat=deprecation")
  }
}
