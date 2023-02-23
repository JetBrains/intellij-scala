package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.notification.{NotificationCategory, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.{IdeModifiableModelsProviderImpl, ProjectDataManager}
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.testFramework.HeavyPlatformTestCase
import org.jetbrains.plugins.scala.project.external.{ShownNotification, ShownNotificationsKey}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import scala.annotation.nowarn

/**
 * TestCase class to use when testing ProjectDataService implementations
 */
abstract class ProjectDataServiceTestCase extends HeavyPlatformTestCase {

  protected def importProjectData(projectData: DataNode[ProjectData]): Unit =
    ProjectDataServiceTestCase.importProjectData(projectData, getProject)

  protected def assertScalaLibraryWarningNotificationShown(project: Project, systemId: ProjectSystemId): Unit = {
    val actualNotifications = Option(project.getUserData(ShownNotificationsKey)).getOrElse(Nil).map(ExpectedNotificationData.apply)
    assertCollectionEquals(
      "Missing scala library notification should be shown",
      Seq(ExpectedNotificationData(systemId, NotificationSource.PROJECT_SYNC, NotificationCategory.WARNING)),
      actualNotifications
    )
  }

  protected def assertNoNotificationShown(project: Project): Unit = {
    val actualNotifications = Option(project.getUserData(ShownNotificationsKey)).getOrElse(Nil).map(ExpectedNotificationData.apply)
    assertCollectionEquals(
      "No notifications expected",
      Seq.empty[ExpectedNotificationData],
      actualNotifications
    )
  }

  protected case class ExpectedNotificationData(systemId: ProjectSystemId, source: NotificationSource, category: NotificationCategory)

  protected object ExpectedNotificationData {
    def apply(notification: ShownNotification): ExpectedNotificationData =
      ExpectedNotificationData(notification.id, notification.data.getNotificationSource, notification.data.getNotificationCategory)
  }
}

object ProjectDataServiceTestCase {

  @nowarn("cat=deprecation")
  def importProjectData(projectData: DataNode[ProjectData], project: Project): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit =
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(() => {
          val projectDataManager = ApplicationManager.getApplication.getService(classOf[ProjectDataManager])
          projectDataManager.importData(projectData, project, new IdeModifiableModelsProviderImpl(project))
        })
    })
}
