package org.jetbrains.sbt.project

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.ui.EditorNotificationProvider
import junit.framework.TestCase.{assertFalse, assertNotNull, assertNull, assertTrue}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.{ExternalSystemImportRootOptions, IdeaProjectFixtureOptions, TestProjectCopyOptions}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path
import java.util.function
import javax.swing.JComponent

// See SCL-23943
@Category(Array(classOf[SlowTests]))
@RunWith(classOf[JUnit4])
class SetupScalaHighlightingNestedSbtProjectTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/setupScalaHighlightingNestedSbtProject"

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_17

  /*
   * SCL-23943 was about Java sources at the IDEA project root being incorrectly treated as
   * "sbt project not loaded" because an sbt project was linked from a nested directory.
   *
   * This test is still an approximation of the original "outer Java module + linked nested sbt module" setup:
   * the runtime test project is copied as a whole and opened as the IDEA project root, while the external-system
   * import root points to the nested sbt project directory. This keeps ProjectUtil.guessProjectDir(project)
   * aligned with the production fix.
   */
  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(
      copyToTemporaryDir = true,
      deleteTempDirectoryOnTestProcessShutDown = false
    )

  override protected def getIdeaProjectFixtureOptions: IdeaProjectFixtureOptions =
    super.getIdeaProjectFixtureOptions.copy(useTestProjectAsIdeaProjectRoot = true)

  override protected def getExternalSystemImportRootOptions: ExternalSystemImportRootOptions =
    super.getExternalSystemImportRootOptions.copy(relativePath = Some("nestedSbtProject"))

  @Test
  def setupScalaHighlighting(): Unit = {
    val helloJavaFile = findVirtualFile(getTestProjectPath / "src" / "HelloJava.java")
    val helloScalaFile = findVirtualFile(getTestProjectPath / "nestedSbtProject" / "src" / "main" / "scala" / "HelloScala.scala")

    assertShouldNotHighlightFile(helloScalaFile, "before the project has been imported")
    assertNoNotificationBannerShown(helloJavaFile, "before the project has been imported")
    assertNoNotificationBannerShown(helloScalaFile, "before the project has been imported")

    importProject(false)

    assertShouldHighlightFile(helloScalaFile, "after the project has been imported")
    assertNoNotificationBannerShown(helloJavaFile, "after the project has been imported")
    assertNoNotificationBannerShown(helloScalaFile, "after the project has been imported")
  }

  private def assertShouldHighlightFile(file: VirtualFile, state: String): Unit = {
    val actual = shouldHighlightFile(file)
    assertTrue(s"${file.getName} should be highlighted $state", actual)
  }

  private def assertShouldNotHighlightFile(file: VirtualFile, state: String): Unit = {
    val actual = shouldHighlightFile(file)
    assertFalse(s"${file.getName} should not be highlighted $state", actual)
  }

  private def shouldHighlightFile(file: VirtualFile): Boolean = {
    val psiFile = findPsiFile(file)
    ProblemHighlightFilter.shouldHighlightFile(psiFile)
  }

  private def assertNoNotificationBannerShown(file: VirtualFile, state: String): Unit = {
    val notificationBanner = collectNotificationData(file)
    assertNull(s"A notification banner should not be shown in ${file.getName} $state", notificationBanner)
  }

  private def collectNotificationData(file: VirtualFile): function.Function[? >: FileEditor, ? <: JComponent] = {
    val notificationProvider = EditorNotificationProvider.EP_NAME.findExtensionOrFail(classOf[SetupScalaHighlightingNotificationProvider], getMyProject)
    notificationProvider.collectNotificationData(getMyProject, file)
  }

  private def findPsiFile(file: VirtualFile): PsiFile = {
    val manager = PsiManager.getInstance(getMyProject)
    val psiFile = manager.findFile(file)
    assertNotNull(s"Could not find psi file for virtual file: $file", psiFile)
    psiFile
  }

  private def findVirtualFile(path: Path): VirtualFile = {
    // It's necessary to refresh the virtual file system to get the up-to-date VirtualFile/PsiFile instances before and after project import.
    val virtualFile = VfsUtil.findFile(path, true)
    assertNotNull(s"Could not find virtual file for path: $path", virtualFile)
    virtualFile
  }
}
