package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.ui.UIUtil

/**
 * TestCase class to use when testing ProjectDataService implementations
 * @author Nikolay Obedin
 * @since 6/5/15.
 */
abstract class ProjectDataServiceTestCase extends PlatformTestCase {
  def importProjectData(projectData: DataNode[ProjectData]): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(getProject) {
      override def execute(): Unit =
        ProjectRootManagerEx.getInstanceEx(getProject).mergeRootsChangesDuring(new Runnable() {
          override def run(): Unit = {
            val projectDataManager = ServiceManager.getService(classOf[ProjectDataManager])
            projectDataManager.importData(projectData, getProject, new IdeModifiableModelsProviderImpl(getProject), true);
          }
        })
    })

  def assertNotificationsCount(source: NotificationSource, category: NotificationCategory, projectSystemId: ProjectSystemId, expected: Integer): Unit = {
    UIUtil.dispatchAllInvocationEvents()
    val actual = ExternalSystemNotificationManager.getInstance(getProject).getMessageCount(source, category, projectSystemId)
    junit.framework.Assert.assertEquals(s"Notification count differs: Expected [ $expected ], Got [ $actual ]", expected, actual)
  }
}
