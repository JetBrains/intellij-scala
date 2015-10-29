package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

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
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManager
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import com.intellij.testFramework.{IdeaTestUtil, PlatformTestUtil}
import com.intellij.util.ThrowableRunnable
import org.jetbrains.SbtStructureSetup
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 10/23/15.
  */
abstract class PerformanceSbtProjectHighlightingTestBase extends ExternalSystemImportingTestCase with SbtStructureSetup {
  implicit class IntExt(val i: Int) extends AnyVal {
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
    if (!outputZipFile.exists() && !projectDir.exists()) { //don't download if zip file is already there
      GithubDownloadUtil.downloadAtomically(null, downloadURL, outputZipFile, githubUsername, githubRepoName)
    }
    if (!projectDir.exists()) { //don't unpack if the project is already unpacked
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

  def doTest(filename: String, timeoutInMillis: Int): Unit = {
    import scala.collection.JavaConversions._
    importProject()
    val searchScope =
      new SourceFilterScope(GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.projectScope(myProject),
        ScalaFileType.SCALA_FILE_TYPE, JavaFileType.INSTANCE), myProject)

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.SCALA_FILE_TYPE, searchScope)
    val file = files.filter(_.getName == filename).toList match {
      case vf :: Nil => vf
      case Nil => //is this a filepath?
        files.find(_.getCanonicalPath == s"$projectDirPath/$filename") match {
          case Some(vf) => vf
          case _ =>
            Assert.assertTrue(s"Could not find file: $filename.\nConsider providing relative path from project root", false)
            return
        }
      case list =>
        Assert.assertTrue(s"There are ${list.size} files with name $filename.\nProvide full path from project root", false)
        return
    }
    LocalFileSystem.getInstance().refreshFiles(files)
    val fileManager: FileManager = PsiManager.getInstance(myProject).asInstanceOf[PsiManagerEx].getFileManager
    PlatformTestUtil.startPerformanceTest(s"Performance test $filename", timeoutInMillis, new ThrowableRunnable[Nothing] {
      override def run(): Unit = {
        val annotator = new ScalaAnnotator
        val mock = new AnnotatorHolderMock

        file.refresh(true, false)
        val psiFile = fileManager.findFile(file)
        val visitor = new ScalaRecursiveElementVisitor {
          override def visitElement(element: ScalaPsiElement) {
            try {
              annotator.annotate(element, mock)
              super.visitElement(element)
            } catch {
              case ignored: Throwable => //this should be checked in AllProjectHighlightingTest
            }
          }
        }
        psiFile.accept(visitor)
        fileManager.cleanupForNextTest()
      }
    }).cpuBound().assertTiming()
  }

  def githubUsername: String

  def githubRepoName: String

  def revision: String
}

