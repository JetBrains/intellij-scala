package org.jetbrains.sbt.project

import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.assertNotNull

import java.io.File
import java.nio.file.Path

abstract class ScalaExternalSystemImportingTestBase extends ExternalSystemImportingTestCase {

  private var myProjectJdk: Sdk = _

  protected def getJdkConfiguredForTestCase: Sdk = myProjectJdk

  protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_11

  override protected def getTestsTempDir: String = "" // Use default temp directory

  override def setUp(): Unit = {
    super.setUp()
    setupProjectJdk()
  }

  protected def setupProjectJdk(): Unit = {
    myProjectJdk = SmartJDKLoader.getOrCreateJDK(projectJdkLanguageLevel)
  }

  /**
   * @return path to the project in the test data directory.
   *         Note, the actual runtime project directory can be changed if [[copyTestProjectToTemporaryDir]] is set to true
   * @example `.../testdata/projectsForHighlightingTests/downloaded/scala3-example-project`
   */
  protected def getTestDataProjectPath: String

  /**
   * When set to true:
   *   - the test will be run on a copy of the project directory from test data
   *   - the original test data project directory will be untouched,
   *
   * When set to false:
   *   - the test will run in the original project directory from test data
   *   - after test is run, the original test data directory can have modified/deleted/new files
   *     which can make the next test run invalid
   */
  protected def copyTestProjectToTemporaryDir: Boolean = false

  protected final lazy val getTestProjectPath: Path = getTestProjectDir.toPath

  /**
   * - Same as [[getTestDataProjectPath]] but as a File when [[copyTestProjectToTemporaryDir]] is false<br>
   * - Temp project directory when [[copyTestProjectToTemporaryDir]] is true
   */
  protected final lazy val getTestProjectDir: File = {
    val originalTestDataProjectDir = new File(getTestDataProjectPath)
    if (!copyTestProjectToTemporaryDir)
      originalTestDataProjectDir
    else {
      val deleteOnExit = true
      val tempProjectDir = FileUtil.createTempDirectory(s"temp_projects/${originalTestDataProjectDir.getName}", "", deleteOnExit)
      println(s"Test project copied to the temporary directory: $tempProjectDir")
      FileUtil.copyDir(originalTestDataProjectDir, tempProjectDir)
      tempProjectDir
    }
  }

  override protected def setUpProjectRoot(): Unit = {
    val testProjectPath = getTestProjectDir
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(testProjectPath)
    assertNotNull(s"test project root was not found: $testProjectPath", myProjectRoot)
  }

  override def tearDown(): Unit = {
    //jdk might be null if it was some exception in super.setup()
    if (myProjectJdk != null) {
      inWriteAction {
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.removeJdk(myProjectJdk)
      }
    }

    super.tearDown()
  }
}
