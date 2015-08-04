package org.jetbrains.sbt
package project

import java.io.File

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSystemSettings
import org.junit.experimental.categories.Category

/**
 * @author Nikolay Obedin
 * @since 8/4/15.
 */
@Category(Array(classOf[SlowTests]))
class ImportingTest extends ExternalSystemImportingTestCase {

  def testSimple = doTest

  def getTestProjectDir: File = {
    val testdataPath = TestUtils.getTestDataPath + "/sbt/projects"
    new File(testdataPath, getTestName(true))
  }

  def scalaPluginBuildOutputDir: File = new File("../../out/plugin/Scala")

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
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(getTestProjectDir)
    val systemSettings = SbtSystemSettings.getInstance(myProject)
    systemSettings.setCustomLauncherEnabled(true)
    systemSettings.setCustomLauncherPath(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher/sbt-launch.jar")
    systemSettings.setCustomSbtStructureDir(scalaPluginBuildOutputDir.getAbsolutePath + "/launcher")
  }

  private def doTest(): Unit = {
    importProject()
  }
}
