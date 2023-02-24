package org.jetbrains.plugins.scala.performance

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.project.TestProjectManager
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.{PlatformTestUtil, TestApplicationManager}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.junit.Assert.assertNotNull

import java.nio.file.Path

final class FixtureDelegate(projectFile: Path) extends IdeaProjectTestFixture {
  private var actualProject: Project = _

  override def getProject: Project = actualProject

  override def getModule: module.Module = {
    import org.jetbrains.plugins.scala.project.ModuleExt
    actualProject.modules.filterNot(_.isBuildModule).head
  }

  //mainly for debugging issue when there are some exceptions in `tearDown`
  private var setUpCalled = false
  private var tearDownCalledStack: Option[Throwable] = None

  override def setUp(): Unit = {
    setUpCalled = true

    TestApplicationManager.getInstance.setDataProvider(null)

    val projectManager = ProjectManagerEx.getInstanceEx.asInstanceOf[TestProjectManager]
    actualProject = projectManager.openProject(projectFile, OpenProjectTask.build())
    assertNotNull(s"Failed to open project $projectFile", actualProject)

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  override def tearDown(): Unit = {
    if (!setUpCalled) {
      throw new IllegalStateException("Calling `tearDown` on a non-initialized fixture, `setUp` wasn't invoked")
    }
    tearDownCalledStack match {
      case Some(ex) =>
        throw new IllegalStateException("Trying to call `tearDown` second time (see stacktrace)", ex)
      case _ =>
        tearDownCalledStack = Some(new RuntimeException())
    }

    TestApplicationManager.tearDownProjectAndApp(getProject)
    actualProject = null
  }
}
