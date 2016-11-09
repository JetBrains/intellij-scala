package org.jetbrains.plugins.scala.performance

import java.io.File
import java.util

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.javascript.boilerplate.GithubDownloadUtil
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.platform.templates.github.ZipUtil
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope, GlobalSearchScopesCore}
import com.intellij.testFramework.{IdeaTestUtil, VfsTestUtil}
import org.jetbrains.SbtStructureSetup
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 11/17/2015
  */
abstract class DownloadingAndImportingTestCase extends ExternalSystemImportingTestCase with SbtStructureSetup {

  implicit class IntExt(val i: Int) {
    def seconds: Int = i * 1000
  }

  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val settings = new SbtProjectSettings
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
    val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk18
    else internalSdk
    settings.setJdk(sdk.getName)
    settings.setCreateEmptyContentRootDirectories(true)
    settings
  }

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
    println("Starting download")
    if (!outputZipFile.exists() && !projectDir.exists()) {
      //don't download if zip file is already there
      GithubDownloadUtil.downloadAtomically(null, downloadURL, outputZipFile, githubUsername, githubRepoName)
    }
    println("Finished download, extracting")
    if (!projectDir.exists()) {
      //don't unpack if the project is already unpacked
      ZipUtil.unzip(null, projectDir, outputZipFile, null, null, true)
    }
    Assert.assertTrue("Project dir does not exist. Download or unpack failed!", projectDir.exists())
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
    }
  }

  override def setUp(): Unit = {
    super.setUp()

    importProject()
  }

  def findFile(filename: String): VirtualFile = {
    import scala.collection.JavaConversions._

    val directoryScope = GlobalSearchScopesCore.directoryScope(myProject, myProjectRoot, true)
    val searchScope =
      new SourceFilterScope(GlobalSearchScope.getScopeRestrictedByFileTypes(directoryScope,
        ScalaFileType.INSTANCE, JavaFileType.INSTANCE), myProject)

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.INSTANCE, searchScope)
    val file = files.filter(_.getName == filename).toList match {
      case vf :: Nil => vf
      case Nil => //is this a filepath?
        val file = VfsTestUtil.findFileByCaseSensitivePath(s"$projectDirPath/$filename")
        if (file != null && files.contains(file)) file
        else {
          Assert.assertTrue(s"Could not find file: $filename.\nConsider providing relative path from project root", false)
          null
        }
      case list =>
        Assert.assertTrue(s"There are ${list.size} files with name $filename.\nProvide full path from project root", false)
        null
    }
    LocalFileSystem.getInstance().refreshFiles(files)
    file
  }

  def githubUsername: String

  def githubRepoName: String

  def revision: String
}

trait ScalaCommunityDownloadingAndImportingTestCase {
  protected def getExternalSystemConfigFileName: String = "build.sbt"

  def githubUsername: String = "JetBrains"

  def githubRepoName: String = "intellij-scala"

  def revision: String = "d2906113e9cdca0e302437cfd412fcb19d288720"
}
