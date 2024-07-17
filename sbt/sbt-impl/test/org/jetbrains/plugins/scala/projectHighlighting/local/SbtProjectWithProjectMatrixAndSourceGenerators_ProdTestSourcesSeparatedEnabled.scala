package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.projectHighlighting.base.SbtProjectHighlightingLocalProjectsTestBase
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.jetbrains.sbt.project.{ExactMatch, ProjectStructureMatcher}

class SbtProjectWithProjectMatrixAndSourceGenerators_ProdTestSourcesSeparatedEnabled
  extends SbtProjectHighlightingLocalProjectsTestBase
    with ProjectStructureMatcher
    with ExactMatch {

  //Note in this test sbt project sources are generated automatically on project reload (in "update" task)
  //but in common build sources are not automatically generated during project reload
  override def projectName = "sbt-projectmatrix-with-source-generators"

  override protected val projectFileName = projectName

  override def setUp(): Unit = {
    val externalProjectSettings = getCurrentExternalProjectSettings
    externalProjectSettings.setSeparateProdAndTestSources(true)
    super.setUp()
  }

  override def testHighlighting(): Unit =
    super.testHighlighting()

  override protected def highlightSingleFile(
    virtualFile: VirtualFile,
    psiFile: PsiFile,
    reporter: HighlightingProgressReporter,
  ): Unit =
    doHighlightingForFile(virtualFile, psiFile, reporter)

  //noinspection ScalaUnusedSymbol,TypeAnnotation
  def testProjectStructure(): Unit = {
    import org.jetbrains.sbt.project.ProjectStructureDsl._

    class myModule(name: String, group: Array[String] = Array.empty, contentRootRelativeName: String = "", contentRootRelativePaths: Seq[String] = Seq.empty) extends module(name, group) {
      def this(name: String, contentRootRelativePaths: Seq[String]) =
        this(name, Array.empty, "", contentRootRelativePaths)

      def this(name: String, group: Array[String], contentRootRelativePaths: Seq[String]) =
        this(name, group, "", contentRootRelativePaths)

      def buildContentRootPath(relativePath: String): String =
        if (relativePath.nonEmpty) s"${getTestProjectDir.toPath}/$relativePath"
        else getTestProjectDir.getAbsolutePath

      val contentRootPaths =
        // When modules are separated to main and test, then content roots are created even for non-existing directories.
        // That's why #relativeProjectPath is only used for .sbt/matrix directories because they will always exist, and for the rest we are not sure.
        if (contentRootRelativePaths.nonEmpty) contentRootRelativePaths.map(buildContentRootPath)
        else Seq(relativeProjectPath(s".sbt/matrix/$contentRootRelativeName"))

      contentRoots := contentRootPaths
      sources := Seq()
      testSources := Seq()
      resources := Seq()
      testResources := Seq()
      moduleDependencies := Seq()
      excluded := Seq()
    }

    def generateRelativePaths(prefix: String, suffix: String): Seq[String] = Seq(
        s"$prefix/src/$suffix",
        s"$prefix/target/jvm-2.13/src_managed/$suffix",
        s"$prefix/target/jvm-2.13/resource_managed/$suffix"
      )

    val expectedProject: project = new project(projectName) {
      val sbtProjectmatrix= new myModule(projectName, Seq("")) { excluded := Seq("target") }
      val sbtProjectmatrixMain = new myModule(
        s"$projectName.main",
        Seq(
          "src/main",
          "target/scala-2.12/src_managed/main",
          "target/scala-2.12/resource_managed/main"
        )
      )
      val sbtProjectmatrixTest = new myModule(
        s"$projectName.test",
        Seq(
          "src/test",
          "target/scala-2.12/src_managed/test",
          "target/scala-2.12/resource_managed/test"
        )
      )
      sbtProjectmatrix.dependsOn(sbtProjectmatrixMain, sbtProjectmatrixTest)
      sbtProjectmatrixTest.dependsOn(sbtProjectmatrixMain)

      val sbtProjectmatrixBuild = new myModule(s"$projectName.sbt-projectmatrix-with-source-generators-build", Seq("project")) {
        sources := Seq("")
        excluded := Seq("target", "project/target")
      }

      val upstreamGroup = Array(projectName, "upstream")
      val upstream = new myModule("upstream", upstreamGroup, "upstream")
      val upstreamMain = new myModule("upstream.main", upstreamGroup) { contentRoots := Seq() }
      val upstreamTest = new myModule("upstream.test", upstreamGroup) { contentRoots := Seq() }
      val upstream2_11 = new myModule("upstream2_11", upstreamGroup, "upstream2_11")
      val upstream2_11Main = new myModule("upstream2_11.main", upstreamGroup) { contentRoots := Seq() }
      val upstream2_11Test = new myModule("upstream2_11.test", upstreamGroup) { contentRoots := Seq() }
      val upstream2_12 = new myModule("upstream2_12", upstreamGroup, "upstream2_12")
      val upstream2_12Main = new myModule("upstream2_12.main", upstreamGroup) { contentRoots := Seq() }
      val upstream2_12Test = new myModule("upstream2_12.test", upstreamGroup) { contentRoots := Seq() }
      val upstreamSources = new myModule("upstream-sources", upstreamGroup, Seq("upstream")) { excluded := Seq("target") }
      val upstreamSourcesMain = new myModule("upstream-sources.main", upstreamGroup, generateRelativePaths("upstream", "main")) {
        sources := Seq("scala", "")
      }
      val upstreamSourcesTest = new myModule("upstream-sources.test", upstreamGroup, generateRelativePaths("upstream", "test"))
      upstream.dependsOn(upstreamMain, upstreamTest)
      upstreamMain.dependsOn(upstreamSourcesMain)
      upstreamTest.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstreamMain)
      upstreamSources.dependsOn(upstreamSourcesTest, upstreamSourcesMain)
      upstreamSourcesTest.dependsOn(upstreamMain)
      upstream2_11.dependsOn(upstream2_11Main, upstream2_11Test)
      upstream2_11Main.dependsOn(upstreamSourcesMain)
      upstream2_11Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_11Main)
      upstream2_12.dependsOn(upstream2_12Test, upstream2_12Main)
      upstream2_12Main.dependsOn(upstreamSourcesMain)
      upstream2_12Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_12Main)

      val downstreamGroup = Array(projectName, "downstream")
      val downstream = new myModule("downstream", downstreamGroup, "downstream")
      val downstreamMain = new myModule("downstream.main", downstreamGroup) { contentRoots := Seq() }
      val downstreamTest = new myModule("downstream.test", downstreamGroup) { contentRoots := Seq() }
      val downstream2_11 = new myModule("downstream2_11", downstreamGroup, "downstream2_11")
      val downstream2_11Main = new myModule("downstream2_11.main", downstreamGroup) { contentRoots := Seq() }
      val downstream2_11Test = new myModule("downstream2_11.test", downstreamGroup) { contentRoots := Seq() }
      val downstream2_12 = new myModule("downstream2_12", downstreamGroup, "downstream2_12")
      val downstream2_12Main = new myModule("downstream2_12.main", downstreamGroup) { contentRoots := Seq() }
      val downstream2_12Test = new myModule("downstream2_12.test", downstreamGroup) { contentRoots := Seq() }
      val downstreamSources = new myModule("downstream-sources", downstreamGroup, Seq("downstream")) { excluded := Seq("target") }
      val downstreamSourcesMain = new myModule("downstream-sources.main", downstreamGroup, generateRelativePaths("downstream", "main")) {
        sources := Seq("scala", "")
      }
      val downstreamSourcesTest = new myModule("downstream-sources.test", downstreamGroup, generateRelativePaths("downstream", "test")) {
        testSources := Seq("")
      }
      downstream.dependsOn(downstreamMain, downstreamTest)
      downstreamMain.dependsOn(upstreamMain, upstreamSourcesMain, downstreamSourcesMain)
      downstreamTest.dependsOn(upstreamMain, downstreamSourcesTest, downstreamMain, upstreamSourcesMain, downstreamSourcesMain)
      downstreamSources.dependsOn(downstreamSourcesMain, downstreamSourcesTest)
      downstreamSourcesMain.dependsOn(upstreamMain)
      downstreamSourcesTest.dependsOn(downstreamMain, upstreamMain)
      downstream2_11.dependsOn(downstream2_11Main, downstream2_11Test)
      downstream2_11Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_11Main)
      downstream2_11Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_11Main, upstreamSourcesMain, upstream2_11Main)
      downstream2_12.dependsOn(downstream2_12Main, downstream2_12Test)
      downstream2_12Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_12Main)
      downstream2_12Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_12Main, upstreamSourcesMain, upstream2_12Main)

      val upstreamBothPlatformsGroup = Array(projectName, "upstreamBothPlatforms")
      val upstreamBothPlatforms = new myModule("upstreamBothPlatforms", upstreamBothPlatformsGroup, "upstreamBothPlatforms")
      val upstreamBothPlatformsMain = new myModule("upstreamBothPlatforms.main", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatformsTest = new myModule("upstreamBothPlatforms.test", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatforms2_11 = new myModule("upstreamBothPlatforms2_11", upstreamBothPlatformsGroup, "upstreamBothPlatforms2_11")
      val upstreamBothPlatforms2_11Main = new myModule("upstreamBothPlatforms2_11.main", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatforms2_11Test = new myModule("upstreamBothPlatforms2_11.test", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatforms2_12 = new myModule("upstreamBothPlatforms2_12", upstreamBothPlatformsGroup, "upstreamBothPlatforms2_12")
      val upstreamBothPlatforms2_12Test = new myModule("upstreamBothPlatforms2_12.test", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatforms2_12Main = new myModule("upstreamBothPlatforms2_12.main", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatformsJS = new myModule("upstreamBothPlatformsJS", upstreamBothPlatformsGroup, "upstreamBothPlatformsJS")
      val upstreamBothPlatformsJSMain = new myModule("upstreamBothPlatformsJS.main", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatformsJSTest = new myModule("upstreamBothPlatformsJS.test", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatformsJS2_11 = new myModule("upstreamBothPlatformsJS2_11", upstreamBothPlatformsGroup, "upstreamBothPlatformsJS2_11")
      val upstreamBothPlatformsJS2_11Main = new myModule("upstreamBothPlatformsJS2_11.main", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatformsJS2_11Test = new myModule("upstreamBothPlatformsJS2_11.test", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatformsJS2_12 = new myModule("upstreamBothPlatformsJS2_12", upstreamBothPlatformsGroup, "upstreamBothPlatformsJS2_12")
      val upstreamBothPlatformsJS2_12Main = new myModule("upstreamBothPlatformsJS2_12.main", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatformsJS2_12Test = new myModule("upstreamBothPlatformsJS2_12.test", upstreamBothPlatformsGroup) { contentRoots := Seq() }
      val upstreamBothPlatformsSources = new myModule("upstreamBothPlatforms-sources", upstreamBothPlatformsGroup, Seq("upstreamBothPlatforms")) { excluded := Seq("target") }
      val upstreamBothPlatformsSourcesMain = new myModule("upstreamBothPlatforms-sources.main", upstreamBothPlatformsGroup, generateRelativePaths("upstreamBothPlatforms", "main")) {
        sources := Seq("scala", "")
      }
      val upstreamBothPlatformsSourcesTest = new myModule("upstreamBothPlatforms-sources.test", upstreamBothPlatformsGroup, generateRelativePaths("upstreamBothPlatforms", "test"))
      upstreamBothPlatforms.dependsOn(upstreamBothPlatformsTest, upstreamBothPlatformsMain)
      upstreamBothPlatformsMain.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsTest.dependsOn(upstreamBothPlatformsMain, upstreamBothPlatformsSourcesTest, upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsSources.dependsOn(upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatformsSourcesTest.dependsOn(upstreamBothPlatformsMain)
      upstreamBothPlatforms2_11Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatforms2_11Test.dependsOn(upstreamBothPlatforms2_11Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatforms2_12.dependsOn(upstreamBothPlatforms2_12Main, upstreamBothPlatforms2_12Test)
      upstreamBothPlatforms2_11.dependsOn(upstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Test)
      upstreamBothPlatforms2_12Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatforms2_12Test.dependsOn(upstreamBothPlatforms2_12Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatformsJS.dependsOn(upstreamBothPlatformsJSMain, upstreamBothPlatformsJSTest)
      upstreamBothPlatformsJSMain.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsJSTest.dependsOn(upstreamBothPlatformsJSMain, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatformsJS2_11.dependsOn(upstreamBothPlatformsJS2_11Main, upstreamBothPlatformsJS2_11Test)
      upstreamBothPlatformsJS2_11Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsJS2_11Test.dependsOn(upstreamBothPlatformsJS2_11Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)
      upstreamBothPlatformsJS2_12.dependsOn(upstreamBothPlatformsJS2_12Main, upstreamBothPlatformsJS2_12Test)
      upstreamBothPlatformsJS2_12Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsJS2_12Test.dependsOn(upstreamBothPlatformsJS2_12Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest)

      val downstreamBothPlatformsGroup = Array(projectName, "downstreamBothPlatforms")
      val downstreamBothPlatforms = new myModule("downstreamBothPlatforms", downstreamBothPlatformsGroup, "downstreamBothPlatforms")
      val downstreamBothPlatformsMain = new myModule("downstreamBothPlatforms.main", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatformsTest = new myModule("downstreamBothPlatforms.test", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatforms2_12 = new myModule("downstreamBothPlatforms2_12", downstreamBothPlatformsGroup, "downstreamBothPlatforms2_12")
      val downstreamBothPlatforms2_12Main = new myModule("downstreamBothPlatforms2_12.main", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatforms2_12Test = new myModule("downstreamBothPlatforms2_12.test", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatforms2_11 = new myModule("downstreamBothPlatforms2_11", downstreamBothPlatformsGroup, "downstreamBothPlatforms2_11")
      val downstreamBothPlatforms2_11Main = new myModule("downstreamBothPlatforms2_11.main", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatforms2_11Test = new myModule("downstreamBothPlatforms2_11.test", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatformsJS = new myModule("downstreamBothPlatformsJS", downstreamBothPlatformsGroup, "downstreamBothPlatformsJS")
      val downstreamBothPlatformsJSMain = new myModule("downstreamBothPlatformsJS.main", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatformsJSTest = new myModule("downstreamBothPlatformsJS.test", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatformsJS2_11 = new myModule("downstreamBothPlatformsJS2_11", downstreamBothPlatformsGroup, "downstreamBothPlatformsJS2_11")
      val downstreamBothPlatformsJS2_11Main = new myModule("downstreamBothPlatformsJS2_11.main", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatformsJS2_11Test = new myModule("downstreamBothPlatformsJS2_11.test", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatformsJS2_12 = new myModule("downstreamBothPlatformsJS2_12", downstreamBothPlatformsGroup, "downstreamBothPlatformsJS2_12")
      val downstreamBothPlatformsJS2_12Main = new myModule("downstreamBothPlatformsJS2_12.main", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatformsJS2_12Test = new myModule("downstreamBothPlatformsJS2_12.test", downstreamBothPlatformsGroup) { contentRoots := Seq() }
      val downstreamBothPlatformsSources = new myModule("downstreamBothPlatforms-sources", downstreamBothPlatformsGroup, Seq("downstreamBothPlatforms")) { excluded := Seq("target") }
      val downstreamBothPlatformsSourcesMain = new myModule("downstreamBothPlatforms-sources.main", downstreamBothPlatformsGroup, generateRelativePaths("downstreamBothPlatforms", "main")) {
        sources := Seq("scala", "")
      }
      val downstreamBothPlatformsSourcesTest = new myModule("downstreamBothPlatforms-sources.test", downstreamBothPlatformsGroup, generateRelativePaths("downstreamBothPlatforms", "test")) {
        testSources := Seq("scala", "")
      }
      downstreamBothPlatforms.dependsOn(downstreamBothPlatformsMain, downstreamBothPlatformsTest)
      downstreamBothPlatformsMain.dependsOn(upstreamBothPlatformsMain, upstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsTest.dependsOn(downstreamBothPlatformsMain, downstreamBothPlatformsSourcesTest, upstreamBothPlatformsMain, upstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsSources.dependsOn(downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest)
      downstreamBothPlatformsSourcesMain.dependsOn(upstreamBothPlatformsMain)
      downstreamBothPlatformsSourcesTest.dependsOn(downstreamBothPlatformsMain, upstreamBothPlatformsMain)
      downstreamBothPlatforms2_12.dependsOn(downstreamBothPlatforms2_12Main, downstreamBothPlatforms2_12Test)
      downstreamBothPlatforms2_12Main.dependsOn(upstreamBothPlatforms2_12Main, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatforms2_12Test.dependsOn(downstreamBothPlatforms2_12Main, upstreamBothPlatforms2_12Main, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatforms2_11.dependsOn(downstreamBothPlatforms2_11Main, downstreamBothPlatforms2_11Test)
      downstreamBothPlatforms2_11Main.dependsOn(upstreamBothPlatforms2_11Main, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatforms2_11Test.dependsOn(downstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Main, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS.dependsOn(downstreamBothPlatformsJSMain, downstreamBothPlatformsJSTest)
      downstreamBothPlatformsJSMain.dependsOn(upstreamBothPlatformsJSMain, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJSTest.dependsOn(upstreamBothPlatformsJSMain, downstreamBothPlatformsJSMain, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS2_11.dependsOn(downstreamBothPlatformsJS2_11Main, downstreamBothPlatformsJS2_11Test)
      downstreamBothPlatformsJS2_11Main.dependsOn(upstreamBothPlatformsJS2_11Main, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS2_11Test.dependsOn(upstreamBothPlatformsJS2_11Main, downstreamBothPlatformsJS2_11Main, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS2_12.dependsOn(downstreamBothPlatformsJS2_12Main, downstreamBothPlatformsJS2_12Test)
      downstreamBothPlatformsJS2_12Main.dependsOn(upstreamBothPlatformsJS2_12Main, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)
      downstreamBothPlatformsJS2_12Test.dependsOn(upstreamBothPlatformsJS2_12Main, downstreamBothPlatformsJS2_12Main, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain)


      val downstreamModules: Seq[myModule] = Seq(
        downstreamSources, downstreamSourcesMain, downstreamSourcesTest,
        downstream, downstreamMain, downstreamTest,
        downstream2_11, downstream2_11Main, downstream2_11Test,
        downstream2_12, downstream2_12Main, downstream2_12Test
      )
      val upstreamModules: Seq[myModule] = Seq(
        upstreamSources, upstreamSourcesMain, upstreamSourcesTest,
        upstream, upstreamMain, upstreamTest,
        upstream2_11, upstream2_11Main, upstream2_11Test,
        upstream2_12, upstream2_12Main, upstream2_12Test
      )
      val downstreamBothPlatformsModules: Seq[myModule] = Seq(
        downstreamBothPlatformsSources, downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest,
        downstreamBothPlatforms, downstreamBothPlatformsMain, downstreamBothPlatformsTest,
        downstreamBothPlatforms2_11, downstreamBothPlatforms2_11Main, downstreamBothPlatforms2_11Test,
        downstreamBothPlatforms2_12, downstreamBothPlatforms2_12Main, downstreamBothPlatforms2_12Test,
        downstreamBothPlatformsJS, downstreamBothPlatformsJSMain, downstreamBothPlatformsJSTest,
        downstreamBothPlatformsJS2_11, downstreamBothPlatformsJS2_11Main, downstreamBothPlatformsJS2_11Test,
        downstreamBothPlatformsJS2_12, downstreamBothPlatformsJS2_12Main, downstreamBothPlatformsJS2_12Test
      )
      val upstreamBothPlatformsModules: Seq[myModule] = Seq(
        upstreamBothPlatforms, upstreamBothPlatformsMain, upstreamBothPlatformsTest,
        upstreamBothPlatformsSources, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest,
        upstreamBothPlatforms2_11, upstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Test,
        upstreamBothPlatforms2_12, upstreamBothPlatforms2_12Main, upstreamBothPlatforms2_12Test,
        upstreamBothPlatformsJS, upstreamBothPlatformsJSMain, upstreamBothPlatformsJSTest,
        upstreamBothPlatformsJS2_11, upstreamBothPlatformsJS2_11Main, upstreamBothPlatformsJS2_11Test,
        upstreamBothPlatformsJS2_12, upstreamBothPlatformsJS2_12Main, upstreamBothPlatformsJS2_12Test
      )

      modules :=
        Seq(sbtProjectmatrix, sbtProjectmatrixMain, sbtProjectmatrixTest, sbtProjectmatrixBuild) ++
        downstreamModules ++
        upstreamModules ++
        downstreamBothPlatformsModules ++
        upstreamBothPlatformsModules
    }

    val matcher = new ProjectStructureMatcher {
      override protected def defaultAssertMatch: ProjectStructureMatcher.AttributeMatchType =
        ProjectStructureMatcher.AttributeMatchType.Exact
    }
    implicit val comparisonOptions: ProjectComparisonOptions = ProjectComparisonOptions(strictCheckForBuildModules = true)
    matcher.assertProjectsEqual(expectedProject, getProject, singleContentRootModules = false)(comparisonOptions)
  }
}
