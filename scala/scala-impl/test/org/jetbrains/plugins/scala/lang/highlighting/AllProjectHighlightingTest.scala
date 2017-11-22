package org.jetbrains.plugins.scala.lang.highlighting

import java.io.File
import java.util

import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.test.ExternalSystemImportingTestCase
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.SbtStructureSetup._
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator}
import org.jetbrains.plugins.scala.finder.SourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{ScalaFileType, SlowTests, extensions}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.experimental.categories.Category

import scala.collection.JavaConverters._

/**
 * @author Alefas
 * @since 12/11/14.
 */

@Category(Array(classOf[SlowTests]))
class AllProjectHighlightingTest extends ExternalSystemImportingTestCase {
  implicit def projectContext: ProjectContext = myProject

  override protected def getCurrentExternalProjectSettings: ExternalProjectSettings = {
    val settings = new SbtProjectSettings
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
    val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk17
    else internalSdk
    settings.jdk = sdk.getName
    settings.setCreateEmptyContentRootDirectories(true)
    settings
  }

  override protected def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override protected def getExternalSystemConfigFileName: String = "build.sbt"

  override protected def getTestsTempDir: String = ""

  protected def getRootDir: String = TestUtils.getTestDataPath + "/projects"

  def testScalaPlugin(): Unit = doRunTest()

  def testDotty(): Unit = doRunTest()

  def doRunTest(): Unit = {
    val projectDir: File = new File(getRootDir, getTestName(false))
    //this test is not intended to ran locally
    if (!projectDir.exists()) {
      println("Project is not found. Test is skipped.")
      return
    }
    importProject()

    extensions.inWriteAction {
      val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
      val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk17
      else internalSdk

      //todo: why we need this??? Looks like sbt integration problem, as we attached SDK as setting
      if (ProjectJdkTable.getInstance().findJdk(sdk.getName) == null) {
        ProjectJdkTable.getInstance().addJdk(sdk)
      }
      ProjectRootManager.getInstance(myProject).setProjectSdk(sdk)
    }

    doRunHighlighting()
  }

  def doRunHighlighting(): Unit = {

    val files: util.Collection[VirtualFile] = FileTypeIndex.getFiles(ScalaFileType.INSTANCE, SourceFilterScope(myProject))

    LocalFileSystem.getInstance().refreshFiles(files)

    val fileManager = PsiManager.getInstance(myProject).asInstanceOf[PsiManagerEx].getFileManager
    val annotator = ScalaAnnotator.forProject

    var percent = 0
    var errorCount = 0
    val size: Int = files.size()

    for ((file, index) <- files.asScala.zipWithIndex) {

      if ((index + 1) * 100 >= (percent + 1) * size) {
        while ((index + 1) * 100 >= (percent + 1) * size) percent += 1
        println(s"Analyzing... $percent%")
      }

      val psi = fileManager.findFile(file)

      val mock = new AnnotatorHolderMock(psi) {
        override def createErrorAnnotation(range: TextRange, message: String): Annotation = {
          errorCount += 1
          println(s"Error in ${file.getName}. Range: $range. Message: $message.")
          super.createErrorAnnotation(range, message)
        }

        override def createErrorAnnotation(elt: PsiElement, message: String): Annotation = {
          errorCount += 1
          println(s"Error in ${file.getName}. Range: ${elt.getTextRange}. Message: $message.")
          super.createErrorAnnotation(elt, message)
        }
      }

      val visitor = new ScalaRecursiveElementVisitor {
        override def visitElement(element: ScalaPsiElement) {
          try {
            annotator.annotate(element, mock)
          } catch {
            case e: Throwable =>
              println(s"Exception in ${file.getName}, Stacktrace: ")
              e.printStackTrace()
              assert(false)
          }
          super.visitElement(element)
        }
      }
      psi.accept(visitor)
    }

    assert(errorCount == 0, s"$errorCount found.")
  }

  override protected def setUpInWriteAction(): Unit = {
    super.setUpInWriteAction()
    val projectDir: File = new File(getRootDir, getTestName(false))
    if (!projectDir.exists()) return
    myProjectRoot = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(projectDir)
    setUpSbtLauncherAndStructure(myProject)
  }
}
