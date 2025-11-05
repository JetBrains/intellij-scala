package org.jetbrains.sbt.project

import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import junit.framework.TestCase.assertNotNull
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}

import java.nio.file.{Files, Path}

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

  /**
   * Same as [[getTestDataProjectPath]] when [[copyTestProjectToTemporaryDir]] is `false`,
   * temp project directory when [[copyTestProjectToTemporaryDir]] is `true`.
   */
  protected final lazy val getTestProjectPath: Path = {
    val originalTestDataProjectDir = Path.of(getTestDataProjectPath)
    if (!copyTestProjectToTemporaryDir)
      originalTestDataProjectDir
    else {
      val projectName = originalTestDataProjectDir.getFileName.toString
      val tmpPath = Files.createTempDirectory(projectName).toRealPath()
      Runtime.getRuntime.addShutdownHook(new Thread(() => NioFiles.deleteRecursively(tmpPath)))
      tmpPath / projectName
    }
  }

  override protected def setUpInWriteAction(): Unit = {
    val originalTestDataProjectDir = Path.of(getTestDataProjectPath)
    val testProjectPath = getTestProjectPath

    if (copyTestProjectToTemporaryDir) {
      println(s"Test project copied to the temporary directory: $testProjectPath")
      NioFiles.copyRecursively(originalTestDataProjectDir, testProjectPath)
    }

    setProjectRoot(testProjectPath)
  }

  final protected def setProjectRoot(projectRoot: Path): Unit = {
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectRoot)
    assertNotNull(s"Could not find a virtual file for: $projectRoot", virtualFile)
    setMyProjectRoot(virtualFile)
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
