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
   * @return path to the project which will be used during the test
   * @example `.../testdata/projectsForHighlightingTests/downloaded/scala3-example-project`
   */
  protected def getTestProjectPath: String

  /**
   * When set to true:
   *   - the test will be run on a copy of the project directory from test data
   *   - the original test data project directory will be untouched,
   *
   * When set to false:
   *   - the test will run in the original project directory from test data
   *   - after test is run, the original test data directory can have modified/deleted/new files
   *     which can make the next test run invalid
   *
   * @todo make tru by default for all inherited classes and rerun tests
   */
  protected def copyTestProjectToTemporaryDir: Boolean = false

  /** Same as [[getTestProjectPath]] but as a File */
  protected final def getTestProjectDir: File = {
    val originalTestDataProjectDir = new File(getTestProjectPath)
    if (!copyTestProjectToTemporaryDir)
      originalTestDataProjectDir
    else {
      val tempProjectDir = FileUtil.createTempDirectory(s"temp_projects/${originalTestDataProjectDir.getName}", "", false)
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
