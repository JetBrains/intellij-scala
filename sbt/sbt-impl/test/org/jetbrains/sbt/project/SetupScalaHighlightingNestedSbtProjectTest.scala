package org.jetbrains.sbt.project

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.ui.EditorNotificationProvider
import junit.framework.TestCase.{assertFalse, assertNotNull, assertNull, assertTrue}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path

@Category(Array(classOf[SlowTests]))
@RunWith(classOf[JUnit4])
class SetupScalaHighlightingNestedSbtProjectTest extends SbtExternalSystemImportingTestLike {
  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/setupScalaHighlightingNestedSbtProject"

  override def getProjectPath: String = Path.of(getTestDataProjectPath, "nestedSbtProject").toString

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_17

  override def setUp(): Unit = {
    super.setUp()
    SbtProjectResolver.processOutputOfLatestStructureDump = ""
    SbtCachesSetupUtil.setupCoursierAndIvyCache(getProject)
  }

  @Test
  def setupScalaHighlighting(): Unit = {
    // SCL-23943
    // This test doesn't exactly correspond to the situation in the linked ticket.
    // We don't really have a way to fully reproduce the inner sbt project within an outer IDEA project using the
    // existing external system test framework. We consulted within our team to confirm this.
    // In this test, the whole project inherits the nested sbt project directory as the base directory.
    // The HelloJava.java file then doesn't even belong to the project instance.
    // But it is still somewhat useful to assert that no notification banner is shown in that file.

    val notificationProvider = EditorNotificationProvider.EP_NAME.findExtensionOrFail(classOf[SetupScalaHighlightingNotificationProvider], getProject)

    val helloJavaPath = getTestProjectPath / "src" / "HelloJava.java"
    val helloScalaPath = getTestProjectPath / "nestedSbtProject" / "src" / "main" / "scala" / "HelloScala.scala"

    val helloScalaPsiFileBefore = findPsiFile(helloScalaPath)

    val shouldHighlightHelloScalaBefore = ProblemHighlightFilter.shouldHighlightFile(helloScalaPsiFileBefore)

    assertFalse("HelloScala.scala should not be highlighted before the project has been imported", shouldHighlightHelloScalaBefore)

    importProject(false)

    val helloJavaPsiFileAfter = findPsiFile(helloJavaPath)
    val helloScalaPsiFileAfter = findPsiFile(helloScalaPath)

    val shouldHighlightHelloScalaAfter = ProblemHighlightFilter.shouldHighlightFile(helloScalaPsiFileAfter)

    val notificationBannerHelloJavaAfter = notificationProvider.collectNotificationData(getProject, helloJavaPsiFileAfter.getVirtualFile)
    val notificationBannerHelloScalaAfter = notificationProvider.collectNotificationData(getProject, helloScalaPsiFileAfter.getVirtualFile)

    assertTrue("HelloScala.scala should be highlighted after the project has been imported", shouldHighlightHelloScalaAfter)
    assertNull("A notification banner should not be shown in HelloJava.java after the project has been imported", notificationBannerHelloJavaAfter)
    assertNull("A notification banner should not be shown in HelloScala.scala after the project has been imported", notificationBannerHelloScalaAfter)
  }

  private def findPsiFile(path: Path): PsiFile = {
    val virtualFile = findVirtualFile(path)
    val manager = PsiManager.getInstance(getProject)
    val psiFile = manager.findFile(virtualFile)
    assertNotNull(s"Could not find psi file for virtual file: $virtualFile", psiFile)
    psiFile
  }

  private def findVirtualFile(path: Path): VirtualFile = {
    // It's necessary to refresh the virtual file system to get the up-to-date VirtualFile/PsiFile instances before
    // and after project import.
    val virtualFile = VfsUtil.findFile(path, true)
    assertNotNull(s"Could not find virtual file for path: $path", virtualFile)
    virtualFile
  }
}
