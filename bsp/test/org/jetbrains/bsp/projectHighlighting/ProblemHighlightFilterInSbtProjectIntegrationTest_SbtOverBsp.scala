package org.jetbrains.bsp.projectHighlighting

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.projectHighlighting.base.ProjectHighlightingAssertions
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.language.SbtFileType
import org.jetbrains.sbt.project.ProjectStructureMatcher
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.junit.Assert.fail

//NOTE:
//The test is very similar to `org.jetbrains.plugins.scala.projectHighlighting.local.SbtFilesProblemHighlightFilterTest`
//But for SBT project which is opened as BSP over SBT
class ProblemHighlightFilterInSbtProjectIntegrationTest_SbtOverBsp
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
        System.err.println(s"File wasn't highlighted because `ProblemHighlightFilter.shouldHighlightFile` returned false: ${TestUtils.getPathRelativeToProject(virtualFile, getProject)}")
    }
  }

  override def testHighlighting(): Unit = {
    assertHighlightedFiles()
    super.testHighlighting()
  }

  private def assertHighlightedFiles(): Unit = {
    ///////////////////////////////
    // .sbt files
    ///////////////////////////////
    assertFileShouldBeHighlighted("build.sbt")
    assertFileShouldBeHighlighted("project/build.sbt")
    assertFileShouldBeHighlighted("sub-project/build.sbt")
    assertFileShouldBeHighlighted("sub-project-separate/build.sbt")

    assertFileShouldNotBeHighlighted("sub-project/testdata/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project/src/main/scala/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project/src/test/scala/build.sbt")

    assertFileShouldNotBeHighlighted("sub-project/testdata/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project/src/main/scala/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project/src/test/scala/build.sbt")

    assertFileShouldNotBeHighlighted("sub-project-separate/testdata/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project-separate/src/main/scala/build.sbt")
    assertFileShouldNotBeHighlighted("sub-project-separate/src/test/scala/build.sbt")

    ///////////////////////////////
    // .scala files
    ///////////////////////////////
    //in `-build` modules
    assertFileShouldBeHighlighted("project/MyClass.scala")
    assertFileShouldBeHighlighted("sub-project-separate/project/MyClass.scala")

    //in sbt project root outside main/test source roots, such files are also treated as sources by sbt
    assertFileShouldBeHighlighted("MyClassInSbtProjectRoot.scala")
    assertFileShouldBeHighlighted("sub-project/MyClassInSbtProjectRoot.scala")
    assertFileShouldBeHighlighted("sub-project-separate/MyClassInSbtProjectRoot.scala")

    //in main/test source roots
    assertFileShouldBeHighlighted("src/main/scala/MyProdClass.scala")
    assertFileShouldBeHighlighted("src/test/scala/MyTestClass.scala")
    assertFileShouldBeHighlighted("sub-project/src/main/scala/MyProdClass.scala")
    assertFileShouldBeHighlighted("sub-project/src/test/scala/MyTestClass.scala")
    assertFileShouldBeHighlighted("sub-project-separate/src/main/scala/MyProdClass.scala")
    assertFileShouldBeHighlighted("sub-project-separate/src/test/scala/MyTestClass.scala")

    //outside source roots
    assertFileShouldNotBeHighlighted("testdata/MyClass.scala")
    assertFileShouldNotBeHighlighted("sub-project/testdata/MyClass.scala")
    assertFileShouldNotBeHighlighted("sub-project-separate/testdata/MyClass.scala")

    ///////////////////////////////
    // .sc files (worksheets)
    ///////////////////////////////
    assertFileShouldBeHighlighted("worksheet.sc")
    assertFileShouldBeHighlighted("src/main/scala/worksheet.sc")
    assertFileShouldBeHighlighted("src/test/scala/worksheet.sc")
    assertFileShouldBeHighlighted("testdata/worksheet.sc")

    assertFileShouldBeHighlighted("sub-project/worksheet.sc")
    assertFileShouldBeHighlighted("sub-project/src/main/scala/worksheet.sc")
    assertFileShouldBeHighlighted("sub-project/src/test/scala/worksheet.sc")
    assertFileShouldBeHighlighted("sub-project/testdata/worksheet.sc")

    assertFileShouldBeHighlighted("sub-project-separate/worksheet.sc")
    assertFileShouldBeHighlighted("sub-project-separate/src/main/scala/worksheet.sc")
    assertFileShouldBeHighlighted("sub-project-separate/src/test/scala/worksheet.sc")
    assertFileShouldBeHighlighted("sub-project-separate/testdata/worksheet.sc")
  }

  private lazy val testProjectDirVFile: VirtualFile =
    VirtualFileManager.getInstance().findFileByNioPath(getTestProjectDir.toPath)

  def relativeProjectPath(relPath: String): String = {
    val result = testProjectDirVFile.findFileByRelativePath(relPath)
    if (result == null) {
      fail(s"Can't find file `$relPath` in `$testProjectDirVFile``")
    }
    result.getPath
  }

  def testProjectStructure(): Unit = {
    import org.jetbrains.sbt.project.ProjectStructureDsl._
    val expectedProject: project = new project(projectName) {
      modules := Seq(
        new module("root") {
          contentRoots := Seq()
          sources := Seq("src/main/scala")
          testSources := Seq("src/test/scala")
          resources := Seq()
          testResources := Seq()
        },
        new module(s"root-build") {
          contentRoots := Seq(relativeProjectPath("project"))
          sources := Seq("Dependencies.scala", "MyClass.scala")
        },
        new module("subProject") {
          sources := Seq("src/main/scala")
          testSources := Seq("src/test/scala")
          resources := Seq()
          testResources := Seq()
        },
        new module("subProjectSeparateRoot") {
          sources := Seq("src/main/scala")
          testSources := Seq("src/test/scala")
          resources := Seq()
          testResources := Seq()
        },
        new module(s"subProjectSeparateRoot-build") {
          contentRoots := Seq(relativeProjectPath("sub-project-separate/project"))
          sources := Seq("MyClass.scala")
        },
      )
    }


    val matcher = new ProjectStructureMatcher {
      override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType = ProjectStructureMatcher.AttributeMatchType.Inexact
    }
    implicit val comparisonOptions: ProjectComparisonOptions = ProjectComparisonOptions(strictCheckForBuildModules = true)
    matcher.assertProjectsEqual(expectedProject, getProject)(comparisonOptions)
  }
}
