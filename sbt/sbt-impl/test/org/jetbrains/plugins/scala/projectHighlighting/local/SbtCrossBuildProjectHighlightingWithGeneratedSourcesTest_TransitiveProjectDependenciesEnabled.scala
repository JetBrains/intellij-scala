package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.sbt.project.ProjectStructureMatcher
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.junit.Assert.fail

class SbtCrossBuildProjectHighlightingWithGeneratedSourcesTest_TransitiveProjectDependenciesEnabled extends SbtProjectHighlightingLocalProjectsTestBase {

  override def projectName = "sbt-crossproject-test-project-with-generated-sources"

  override protected def highlightSingleFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit =
    doHighlightingForFile(virtualFile, psiFile, reporter)

  //noinspection ScalaUnusedSymbol,TypeAnnotation
  def testProjectStructure(): Unit = {
    import org.jetbrains.sbt.project.ProjectStructureDsl._

    class myModule(name: String, contentRootRelativePaths: Option[Seq[String]] = None) extends module(name) {
      def this(name: String, contentRootRelativePaths: String) = {
        this(name, Some(Seq(contentRootRelativePaths)))
      }

      //explicitly define attributes to some default values to ensure that they will be tested
      //(those attributes, which are not defined are not tested)

      contentRoots := contentRootRelativePaths.getOrElse(Nil).map(relativeProjectPath)
      sources := Seq()
      testSources := Seq()
      resources := Seq()
      testResources := Seq()
      moduleDependencies := Seq()
      excluded := Seq("target")
    }

    class myNonRootModule(name: String, contentRootRelativePaths: Option[Seq[String]] = None) extends myModule(s"$projectName.$name", contentRootRelativePaths) {
      def this(name: String, contentRootRelativePaths: String) = {
        this(name, Some(Seq(contentRootRelativePaths)))
      }
    }

    val expectedProject: project = new project(projectName) {
      val `sbt-crossproject-test-project-with-generated-sources` = new myModule(
        "sbt-crossproject-test-project-with-generated-sources",
        ""
      )
      val `sbt-crossproject-test-project-with-generated-sources-build` = new myNonRootModule(
        "sbt-crossproject-test-project-with-generated-sources-build",
        "project"
      ) {
        sources := Seq("")
        excluded := Seq("target", "project/target")
      }

      val `downstreamPure-sources` = new myNonRootModule("downstreamPure.downstreamPure-sources", "downstreamPure") {
        sources := Seq("src/main/scala")
      }
      val `downstreamPureJS` = new myNonRootModule("downstreamPure.downstreamPureJS", "downstreamPure/.js") {
        sources := Seq("target/scala-2.13/src_managed/main")
      }
      val `downstreamPureJVM` = new myNonRootModule("downstreamPure.downstreamPureJVM", "downstreamPure/.jvm") {
        sources := Seq("target/scala-2.13/src_managed/main")
      }
      val `upstreamPure-sources` = new myNonRootModule("upstreamPure.upstreamPure-sources", "upstreamPure") {
        sources := Seq("src/main/scala")
      }
      val `upstreamPureJS` = new myNonRootModule("upstreamPure.upstreamPureJS", "upstreamPure/.js") {
        sources := Seq("target/scala-2.13/src_managed/main")
      }
      val `upstreamPureJVM` = new myNonRootModule("upstreamPure.upstreamPureJVM", "upstreamPure/.jvm") {
        sources := Seq("target/scala-2.13/src_managed/main")
      }

      val `downstreamFull-sources` = new myNonRootModule("downstreamFull.downstreamFull-sources", "downstreamFull/shared") {
        sources := Seq("src/main/scala")
      }
      val `downstreamFullJS` = new myNonRootModule("downstreamFull.downstreamFullJS", "downstreamFull/js") {
        sources := Seq("target/scala-2.13/src_managed/main")
      }
      val `downstreamFullJVM` = new myNonRootModule("downstreamFull.downstreamFullJVM", "downstreamFull/jvm") {
        sources := Seq("target/scala-2.13/src_managed/main")
      }
      val `upstreamFull-sources` = new myNonRootModule("upstreamFull.upstreamFull-sources", "upstreamFull/shared") {
        sources := Seq("src/main/scala")
      }
      val `upstreamFullJS` = new myNonRootModule("upstreamFull.upstreamFullJS", "upstreamFull/js") {
        sources := Seq("target/scala-2.13/src_managed/main")
      }
      val `upstreamFullJVM` = new myNonRootModule("upstreamFull.upstreamFullJVM", "upstreamFull/jvm") {
        sources := Seq("target/scala-2.13/src_managed/main")
      }

      //
      // Define module groups separately for better test data readability
      //
      val downstreamPureGroup: Seq[myModule] = Seq(
        `downstreamPure-sources`,
        `downstreamPureJS`,
        `downstreamPureJVM`,
      )
      val upstreamPureGroup: Seq[myModule] = Seq(
        `upstreamPure-sources`,
        `upstreamPureJS`,
        `upstreamPureJVM`,
      )
      val downstreamFullGroup: Seq[myModule] = Seq(
        `downstreamFull-sources`,
        `downstreamFullJS`,
        `downstreamFullJVM`,
      )
      val upstreamFullGroup: Seq[myModule] = Seq(
        `upstreamFull-sources`,
        `upstreamFullJS`,
        `upstreamFullJVM`,
      )

      downstreamPureGroup.foreach(_.group = Array("downstreamPure"))
      upstreamPureGroup.foreach(_.group = Array("upstreamPure"))
      downstreamFullGroup.foreach(_.group = Array("downstreamFull"))
      upstreamFullGroup.foreach(_.group = Array("upstreamFull"))

      //
      // Define dependencies between modules separately for better test data readability
      //
      `downstreamPure-sources`.dependsOn(`upstreamPureJVM`)
      `downstreamPureJVM`.dependsOn(`upstreamPureJVM`, `downstreamPure-sources`, `upstreamPure-sources`)
      `downstreamPureJS`.dependsOn(`upstreamPureJS`, `downstreamPure-sources`, `upstreamPure-sources`)

      `upstreamPure-sources`.dependsOn()
      `upstreamPureJVM`.dependsOn(`upstreamPure-sources`)
      `upstreamPureJS`.dependsOn(`upstreamPure-sources`)

      `downstreamFull-sources`.dependsOn(`upstreamFullJVM`)
      `downstreamFullJVM`.dependsOn(`upstreamFullJVM`, `downstreamFull-sources`, `upstreamFull-sources`)
      `downstreamFullJS`.dependsOn(`upstreamFullJS`, `downstreamFull-sources`, `upstreamFull-sources`)

      `upstreamFull-sources`.dependsOn()
      `upstreamFullJVM`.dependsOn(`upstreamFull-sources`)
      `upstreamFullJS`.dependsOn(`upstreamFull-sources`)

      modules := Seq(
        `sbt-crossproject-test-project-with-generated-sources`,
        `sbt-crossproject-test-project-with-generated-sources-build`,
      ) ++
        downstreamPureGroup ++
        upstreamPureGroup ++
        downstreamFullGroup ++
        upstreamFullGroup
    }

    val matcher = new ProjectStructureMatcher {
      override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType =
        ProjectStructureMatcher.AttributeMatchType.Exact
    }
    implicit val comparisonOptions: ProjectComparisonOptions = ProjectComparisonOptions(strictCheckForBuildModules = true)
    matcher.assertProjectsEqual(expectedProject, getProject)(comparisonOptions)
  }

  private lazy val testProjectDirVFile: VirtualFile =
    VirtualFileManager.getInstance().findFileByNioPath(getTestProjectDir.toPath)

  private def relativeProjectPath(relPath: String): String = {
    //force refresh, because otherwise sometimes it can old data from previous tests runs (cached in test system directory)
    testProjectDirVFile.refresh(false, true)
    val result = testProjectDirVFile.findFileByRelativePath(relPath)
    if (result == null) {
      fail(s"Can't find file `$relPath` in `$testProjectDirVFile``")
    }
    result.getPath
  }
}
