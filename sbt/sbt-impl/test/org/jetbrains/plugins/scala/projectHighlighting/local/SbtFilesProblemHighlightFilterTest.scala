package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.projectHighlighting.base.{ProjectHighlightingAssertions, SbtProjectHighlightingLocalProjectsTestBase}
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.language.SbtFileType

class SbtFilesProblemHighlightFilterTest
  extends SbtProjectHighlightingLocalProjectsTestBase
    with ProjectHighlightingAssertions {

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
        System.err.println(s"File wasn't highlighted because `ProblemHighlightFilter.shouldHighlightFile` returned false: ${TestUtils.getPathRelativeToProject(virtualFile, getProject)}")
    }
  }

  override def testHighlighting(): Unit = {
    assertFileShouldBeHighlighted("build.sbt")
    assertFileShouldBeHighlighted("project/build.sbt")
    assertFileShouldBeHighlighted("sub-project/build.sbt")
    assertFileShouldBeHighlighted("sub-project-separate/build.sbt")

    assertFileShouldNotBeHighlighted("sub-project/testdata/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project/src/main/scala/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project-separate/testdata/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project-separate/src/main/scala/build.sbt")

    super.testHighlighting()
  }
}
