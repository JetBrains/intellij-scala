package org.jetbrains.plugins.scala.performance

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.project.TestProjectManager
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.{PlatformTestUtil, TestApplicationManager}
import org.jetbrains.plugins.scala.extensions.withProgressSynchronously
import org.jetbrains.plugins.scala.project.ProjectExt

import java.nio.file.Path

class FixtureDelegate(projectFile: Path) extends IdeaProjectTestFixture {
  private var actualProject: Project = _

  override def getProject: Project = actualProject

  override def getModule: module.Module = {
    import org.jetbrains.plugins.scala.project.ModuleExt
    actualProject.modules.filterNot(_.isBuildModule).head
  }

  override def setUp(): Unit = {
    TestApplicationManager.getInstance.setDataProvider(null)

    val projectManager = ProjectManagerEx.getInstanceEx.asInstanceOf[TestProjectManager]
    actualProject = projectManager.openProject(projectFile, OpenProjectTask.build())

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  override def tearDown(): Unit = {
    TestApplicationManager.tearDownProjectAndApp(getProject)
    actualProject = null
  }
}
