package org.jetbrains.sbt.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert.assertNotNull

import java.io.File
import scala.annotation.nowarn

abstract class ImportingTestCase extends ExternalSystemImportingTestCase with ProjectStructureMatcher {

  val Log = Logger.getInstance(this.getClass)

  def testProjectDir: File = {
    val testdataPath = TestUtils.getTestDataPath + "/sbt/projects"
    new File(testdataPath, getTestName(true))
  }

  def runTest(expected: project)
             (implicit compareOptions: ProjectComparisonOptions): Unit = {
    importProject()
    assertProjectsEqual(expected, myProject)
    assertNoNotificationsShown(myProject)
  }

  def runTestWithSdk(sdk: com.intellij.openapi.projectRoots.Sdk, expected: project)
                    (implicit compareOptions: ProjectComparisonOptions): Unit = {
    importProject(sdk)
    assertProjectsEqual(expected, myProject)
    assertNoNotificationsShown(myProject)
  }

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getTestsTempDir: String = "" // Use default temp directory

  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val settings = new SbtProjectSettings
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk: @nowarn("cat=deprecation")
    settings.jdk = internalSdk.getName
    settings
  }

  override protected def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()
    setUpProjectDirectory()
  }

  private def setUpProjectDirectory(): Unit = {
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(testProjectDir)
    assertNotNull("project root was not found: " + testProjectDir, myProjectRoot)
  }
}

