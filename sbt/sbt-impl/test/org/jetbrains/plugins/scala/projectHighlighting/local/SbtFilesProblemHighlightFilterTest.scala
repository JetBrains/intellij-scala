package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.sbt.language.SbtFileType
import org.junit.Assert.assertEquals

class SbtFilesProblemHighlightFilterTest extends SbtProjectHighlightingLocalProjectsTestBase {

  override def projectName = "sbt-with-many-sbt-files-in-different-locations"

  /**
   * This test doesn't work properly with project caching<br>
   * This is because some of our code related to SBT support relies on the fact that some data is stored as "external project data"
   * (see [[org.jetbrains.sbt.SbtUtil#getModuleData]]
   * And when we reuse project using `.ipr` file it is not detected as a external project...
   */
  override protected def isProjectCachingEnabled: Boolean = false

  override protected def scalaFileTypes: Seq[FileType] = Seq(ScalaFileType.INSTANCE, SbtFileType)

  override protected def processOnlyFilesInSourceRoots: Boolean = false

  override protected def highlightSingleFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit = {
    try {
      doHighlightingForFile(virtualFile, psiFile, reporter)
    } catch {
      case ex: java.lang.IllegalStateException if ex.getMessage.contains("ProblemHighlightFilter.shouldHighlightFile") =>
      //skip files which can't be highlighted
      //exception is thrown here:
      // com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.instantiateAndRun(com.intellij.psi.PsiFile, com.intellij.openapi.editor.Editor, int[], boolean, boolean)
      // when we try to highlight files for which we disable highlighting using `ProblemHighlightFilter` API.
    }
  }

  override def testHighlighting(): Unit = {
    super.testHighlighting()

    assertFileShouldBeHighlighted("build.sbt")
    assertFileShouldBeHighlighted("project/build.sbt")
    assertFileShouldBeHighlighted("sub-project/build.sbt")

    assertFileShouldNotBeHighlighted("sub-project/testdata/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project/src/main/scala/build.sbt")
  }

  private def assertFileShouldBeHighlighted(relativePath: String): Unit =
    assertFileShouldBeHighlighted(relativePath, shouldHighlight = true)

  private def assertFileShouldNotBeHighlighted(relativePath: String): Unit =
    assertFileShouldBeHighlighted(relativePath, shouldHighlight = false)

  private def assertFileShouldBeHighlighted(relativePath: String, shouldHighlight: Boolean): Unit = {
    val project = getProject
    val projectRoot = ProjectUtil.guessProjectDir(project);
    val file = projectRoot.findFileByRelativePath(relativePath)
    val psiFile = PsiManager.getInstance(project).findFile(file)
    val shouldHighlightActual = ProblemHighlightFilter.shouldHighlightFile(psiFile)
    val message = if (shouldHighlight)
      s"File must be highlighted: $relativePath"
    else
      s"File must not be highlighted: $relativePath"
    assertEquals(message, shouldHighlight, shouldHighlightActual)
  }
}
