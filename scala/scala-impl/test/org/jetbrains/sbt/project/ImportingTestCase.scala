package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.junit.Assert.assertNotNull

import java.io.File

abstract class ImportingTestCase extends SbtExternalSystemImportingTestCase with ProjectStructureMatcher {

  def getTestProjectDir: File = {
    val testdataPath = TestUtils.getTestDataPath + "/sbt/projects"
    new File(testdataPath, getTestName(true))
  }

  def runTest(expected: project)
             (implicit compareOptions: ProjectComparisonOptions): Unit = {
    importProject(false)
    assertProjectsEqual(expected, myProject)
    assertNoNotificationsShown(myProject)
  }

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getTestsTempDir: String = "" // Use default temp directory

  override protected def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()
    setUpProjectDirectory()
  }

  private def setUpProjectDirectory(): Unit = {
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(getTestProjectDir)
    assertNotNull("project root was not found: " + getTestProjectDir, myProjectRoot)
  }
}

