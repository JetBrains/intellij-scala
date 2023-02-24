package org.jetbrains.sbt.project

import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
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

  /** Same as [[getTestProjectPath]] but as a File */
  protected final def getTestProjectDir: File = new File(getTestProjectPath)

  override protected def setUpProjectRoot(): Unit = {
    val testProjectPath = new File(getTestProjectPath)
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
