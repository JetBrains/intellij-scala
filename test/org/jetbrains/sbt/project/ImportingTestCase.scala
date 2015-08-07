package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSystemSettings

/**
 * @author Nikolay Obedin
 * @since 8/4/15.
 */
abstract class ImportingTestCase extends ExternalSystemImportingTestCase with ProjectStructureMatcher {

  def testProjectDir: File = {
    val testdataPath = TestUtils.getTestDataPath + "/sbt/projects"
    new File(testdataPath, getTestName(true))
  }

  def scalaPluginBuildOutputDir: File = new File("../../out/plugin/Scala")

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
    setUpSbtLauncherAndStructure()
    setUpExternalSystemToPerformImportInIdeaProcess()
  }

  private def setUpProjectDirectory(): Unit =
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(testProjectDir)

  private def setUpSbtLauncherAndStructure(): Unit = {
    val systemSettings = SbtSystemSettings.getInstance(myProject)
    systemSettings.setCustomLauncherEnabled(true)
    systemSettings.setCustomLauncherPath(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher/sbt-launch.jar")
    systemSettings.setCustomSbtStructureDir(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher")
  }

  private def setUpExternalSystemToPerformImportInIdeaProcess(): Unit =
    Registry.get(SbtProjectSystem.Id + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX).setValue(true)
}

