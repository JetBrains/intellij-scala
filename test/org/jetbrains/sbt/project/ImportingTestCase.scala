package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.SbtStructureSetup
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSystemSettings

/**
 * @author Nikolay Obedin
 * @since 8/4/15.
 */
abstract class ImportingTestCase extends ExternalSystemImportingTestCase with ProjectStructureMatcher with SbtStructureSetup {

  val Log = Logger.getInstance(this.getClass)

  def testProjectDir: File = {
    val testdataPath = TestUtils.getTestDataPath + "/sbt/projects"
    new File(testdataPath, getTestName(true))
  }

  def runTest(expected: project): Unit = {
    importProject()
    assertProjectsEqual(expected, myProject)
  }

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getTestsTempDir: String = "" // Use default temp directory

  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val settings = new SbtProjectSettings
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
    settings.setJdk(internalSdk.getName)
    settings
  }

  override protected def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()
    setUpProjectDirectory()
    setUpSbtLauncherAndStructure(myProject)
  }

  private def setUpProjectDirectory(): Unit =
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(testProjectDir)
}

