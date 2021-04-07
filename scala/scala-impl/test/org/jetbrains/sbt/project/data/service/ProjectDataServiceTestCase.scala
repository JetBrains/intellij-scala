package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.testFramework.HeavyPlatformTestCase

/**
 * TestCase class to use when testing ProjectDataService implementations
 * @author Nikolay Obedin
 * @since 6/5/15.
 */
abstract class ProjectDataServiceTestCase extends HeavyPlatformTestCase {
  def importProjectData(projectData: DataNode[ProjectData]): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(getProject) {
      override def execute(): Unit =
        ProjectRootManagerEx.getInstanceEx(getProject).mergeRootsChangesDuring(() => {
          val projectDataManager = ApplicationManager.getApplication.getService(classOf[ProjectDataManager])
          projectDataManager.importData(projectData, getProject, new IdeModifiableModelsProviderImpl(getProject), true)
        })
    })
}
