package org.jetbrains.plugins.scala.performance
import java.nio.file.Path

import com.intellij.openapi.module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.{PlatformTestUtil, TestApplicationManager, TestApplicationManagerKt}
import org.jetbrains.plugins.scala.project.ProjectExt

class FixtureDelegate(projectFile: Path) extends IdeaProjectTestFixture {
    private var actualProject: Project = _
    override def getProject: Project = actualProject
    override def getModule: module.Module = actualProject.modules.head
    override def setUp(): Unit = {
      TestApplicationManager.getInstance.setDataProvider(null)
      actualProject = ProjectManagerEx.getInstanceEx.loadProject(projectFile)
      ProjectManagerEx.getInstanceEx.openProject(actualProject)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    override def tearDown(): Unit = {
      TestApplicationManagerKt.tearDownProjectAndApp(actualProject)
      actualProject = null
    }
  }