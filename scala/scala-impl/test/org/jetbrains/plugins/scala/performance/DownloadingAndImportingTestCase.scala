package org.jetbrains.plugins.scala.performance

import java.io.File
import java.util

import com.intellij.lang.javascript.boilerplate.GithubDownloadUtil
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.platform.templates.github.ZipUtil
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScopesCore}
import com.intellij.testFramework.{IdeaTestUtil, VfsTestUtil}
import org.jetbrains.SbtStructureSetup._
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert
import scala.collection.JavaConverters._

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/17/2015
  */
abstract class DownloadingAndImportingTestCase extends ExternalSystemImportingTestCase {

  implicit class IntExt(val i: Int) {
    def seconds: Int = i * 1000
  }

  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val settings = new SbtProjectSettings
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
    val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk18
    else internalSdk
    settings.jdk = sdk.getName
    settings.setCreateEmptyContentRootDirectories(true)
    settings
  }

  protected val reporter = ProgressReporter.newInstance(getClass.getName)

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getTestsTempDir: String = ""

  def rootDirPath: String = s"${TestUtils.getTestDataPath}/projects"

  def projectDirPath: String = s"$rootDirPath/$githubRepoName"

  def downloadURL: String = s"https://github.com/$githubUsername/$githubRepoName/archive/$revision.zip"

  def outputZipFileName = s"$rootDirPath/zipFiles/$githubRepoName-$githubUsername-$revision"

  override def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()
    val outputZipFile = new File(outputZipFileName)
    val projectDir = new File(projectDirPath)
    if (!outputZipFile.exists() && !projectDir.exists()) {
      //don't download if zip file is already there
      reporter.notify("Starting download")
      GithubDownloadUtil.downloadAtomically(reporter.progressIndicator, downloadURL, outputZipFile, githubUsername, githubRepoName)
    } else { reporter.notify("Project files already exist, skipping download") }
    if (!projectDir.exists()) {
      //don't unpack if the project is already unpacked
      reporter.notify("Finished download, extracting")
      ZipUtil.unzip(null, projectDir, outputZipFile, null, null, true)
    } else { reporter.notify("Project files already extracted") }
    Assert.assertTrue("Project dir does not exist. Download or unpack failed!", projectDir.exists())
    reporter.notify("Finished extracting, starting sbt setup")
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(projectDir)
    setUpSbtLauncherAndStructure(myProject)
    extensions.inWriteAction {
      val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
      val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk17
      else internalSdk

      if (ProjectJdkTable.getInstance().findJdk(sdk.getName) == null) {
        ProjectJdkTable.getInstance().addJdk(sdk)
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

  def githubUsername: String

  def githubRepoName: String

  def revision: String
}

trait ScalaCommunityGithubRepo {

  def githubUsername: String = "JetBrains"

  def githubRepoName: String = "intellij-scala"

  def revision: String = "a9ac902e8930c520b390095d9e9346d9ae546212"
}
