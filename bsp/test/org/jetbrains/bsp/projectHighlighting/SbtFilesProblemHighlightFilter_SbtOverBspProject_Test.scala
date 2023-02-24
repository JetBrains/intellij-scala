package org.jetbrains.bsp.projectHighlighting

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.projectHighlighting.base.ProjectHighlightingAssertions
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.{HighlightingTests, ScalaFileType}
import org.jetbrains.sbt.language.SbtFileType
import org.junit.experimental.categories.Category

//NOTE:
//The test is very similar to `org.jetbrains.plugins.scala.projectHighlighting.local.SbtFilesProblemHighlightFilterTest`
//But for SBT project which is opened as BSP over SBT
@Category(Array(classOf[HighlightingTests]))
class SbtFilesProblemHighlightFilter_SbtOverBspProject_Test
  extends SbtOverBspProjectHighlightingLocalProjectsTestBase
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
    }
  }

  override def testHighlighting(): Unit = {
    super.testHighlighting() //TODO

    assertFileShouldBeHighlighted("build.sbt")
    assertFileShouldBeHighlighted("project/build.sbt")
    assertFileShouldBeHighlighted("sub-project/build.sbt")

    assertFileShouldNotBeHighlighted("sub-project/testdata/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project/src/main/scala/build.sbt")
  }

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "sub-project/build.sbt" -> Set(
      (0, 21), // Unused import statement
      (7, 19), // Cannot resolve symbol Dependencies
      (23, 44), // Expression type Def.Setting[String] must conform to DslEntry in sbt file
      (45, 63), // Expression type Def.Setting[String] must conform to DslEntry in sbt file
      (65, 72), // Cannot resolve symbol Compile
      (73, 74), // Cannot resolve symbol /
      (79, 80), // Cannot resolve symbol /
      (91, 93), // Cannot resolve symbol :=
      (123, 190), // Expression type Def.Setting[Seq[ModuleID]] must conform to DslEntry in sbt file
      (154, 163), // Cannot resolve symbol scalaTest
    )
  )
}
