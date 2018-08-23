package org.jetbrains.plugins.scala.performance

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScopesCore}
import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, IdeaTestFixtureFactory}
import com.intellij.testFramework.{IdeaTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
  * Nikolay.Tropin
  * 14-Dec-17
  */
abstract class ImportingProjectTestCase extends ExternalSystemImportingTestCase {

  protected var codeInsightFixture: CodeInsightTestFixture = _

  override def setUpFixtures(): Unit = {
    val factory = IdeaTestFixtureFactory.getFixtureFactory
    val projectFixture = factory.createFixtureBuilder(getName).getFixture
    codeInsightFixture = factory.createCodeInsightFixture(projectFixture)
    codeInsightFixture.setUp()
    myTestFixture = codeInsightFixture
  }


  override protected def tearDownFixtures(): Unit = {
    codeInsightFixture.tearDown()
    codeInsightFixture = null
    myTestFixture = null
  }

  override protected def getExternalSystemConfigFileName: String = "build.sbt"

  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val settings = new SbtProjectSettings
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
    val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk18
    else internalSdk
    settings.jdk = sdk.getName
    settings.setCreateEmptyContentRootDirectories(true)
    settings
  }

  def filesWithProblems: Map[String, Set[TextRange]] = Map.empty

  protected val reporter = ProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems)

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getTestsTempDir: String = ""

  def rootDirPath: String

  def projectName: String

  def projectDirPath: String = s"$rootDirPath/$projectName"

  def doBeforeImport(): Unit = {}

  override def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()

    doBeforeImport()

    val projectDir = new File(projectDirPath)

    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(projectDir)
    extensions.inWriteAction {
      val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
      val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk18
      else internalSdk

      if (ProjectJdkTable.getInstance().findJdk(sdk.getName) == null) {
        ProjectJdkTable.getInstance().addJdk(sdk, myProject)
      }
      ProjectRootManager.getInstance(myProject).setProjectSdk(sdk)
      reporter.notify("Finished sbt setup, starting import")
    }
  }

  override def setUp(): Unit = {
    super.setUp()

    importProject()
  }

  def findFile(filename: String): VirtualFile = {

    val searchScope = SourceFilterScope(myProject, GlobalSearchScopesCore.directoryScope(myProject, myProjectRoot, true))

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.INSTANCE, searchScope)
    val file = files.asScala.filter(_.getName == filename).toList match {
      case vf :: Nil => vf
      case Nil => // is this a file path?
        val file = VfsTestUtil.findFileByCaseSensitivePath(s"$projectDirPath/$filename")
        Assert.assertTrue(
          s"Could not find file: $filename. Consider providing relative path from project root",
          file != null && files.contains(file)
        )
        file
      case list =>
        Assert.fail(s"There are ${list.size} files with name $filename. Provide full path from project root")
        null
    }
    LocalFileSystem.getInstance().refreshFiles(files)
    file
  }
}
