package org.jetbrains.plugins.scala.performance
import com.intellij.openapi.module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.project.TestProjectManager
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.{PlatformTestUtil, TestApplicationManager}
import org.jetbrains.plugins.scala.project.ProjectExt

import java.nio.file.Path

class FixtureDelegate(projectFile: Path) extends IdeaProjectTestFixture {
    private var actualProject: Project = _
    override def getProject: Project = actualProject
    override def getModule: module.Module = actualProject.modules.head
    override def setUp(): Unit = {
      TestApplicationManager.getInstance.setDataProvider(null)
      actualProject = ProjectManagerEx.getInstanceEx.loadProject(projectFile)
      ProjectManagerEx.getInstanceEx.asInstanceOf[TestProjectManager].openProject(actualProject)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    override def tearDown(): Unit = {
      TestApplicationManager.tearDownProjectAndApp(getProject)
      actualProject = null
    }
  }
