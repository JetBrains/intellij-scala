package org.jetbrains.sbt.project

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.PlatformFacade
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase

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
            val platformFacade = ServiceManager.getService(classOf[PlatformFacade])
            projectDataManager.importData(projectData.getKey, java.util.Collections.singleton(projectData), getProject, platformFacade, true)
          }
        })
    })


  def assertException[T <: Throwable](expectedMessage: Option[String])(closure: => Unit)(implicit m: Manifest[T]): Unit =
    assertException(new AbstractExceptionCase[T]() {
      override def getExpectedExceptionClass(): Class[T] = m.runtimeClass.asInstanceOf[Class[T]]
      override def tryClosure() = closure
    }, expectedMessage.orNull)
}
