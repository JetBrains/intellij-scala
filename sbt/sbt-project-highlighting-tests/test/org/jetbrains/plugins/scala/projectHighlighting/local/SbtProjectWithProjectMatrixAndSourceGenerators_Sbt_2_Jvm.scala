package org.jetbrains.plugins.scala.projectHighlighting.local

import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener

class SbtProjectWithProjectMatrixAndSourceGenerators_Sbt_2_Jvm
  extends SbtProjectWithProjectMatrixAndSourceGenerators_Sbt_2_TestBase {

  override def projectName = "sbt-projectmatrix-with-source-generators-sbt2"

  def testProjectStructure(): Unit = {
    val notificationsCollector = CollectingNotificationsListener.subscribeOnWarningsAndErrors(getProject)

    importProject(false)
    val expectedProject = buildExpectedProjectStructure()
    assertProjectStructure(expectedProject, notificationsCollector)
  }

  //noinspection ScalaUnusedSymbol,TypeAnnotation
  private def buildExpectedProjectStructure(): org.jetbrains.sbt.project.ProjectStructureDsl.project = {
    import org.jetbrains.sbt.project.ProjectStructureDsl.*

    new project(projectName) {
      val sbtProjectmatrix = new myModule(projectName, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%")
      }
      val sbtProjectmatrixMain = new myModule(s"$projectName.main") {
        standardRoots(this, "main", "2.12", "17")
      }
      val sbtProjectmatrixTest = new myModule(s"$projectName.test") {
        standardRoots(this, "test", "2.12", "17")
      }
      sbtProjectmatrix.dependsOn(sbtProjectmatrixMain, sbtProjectmatrixTest)
      sbtProjectmatrixTest.dependsOn(sbtProjectmatrixMain)

      val sbtProjectmatrixBuild = new myModule(s"$projectName.$projectName-build") {
        contentRoots := Seq("%PROJECT_ROOT%/project")
        sources := Seq("%PROJECT_ROOT%/project")
        excluded := Seq("target", "project/target")
      }

      val upstreamGroup = Array(projectName, "upstream")
      val upstream2_11 = new myModule("upstream2_11", upstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream2_11")
      }
      val upstream2_11Main = new myModule("upstream2_11.main", upstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "upstream", "main", "2.11", "12", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val upstream2_11Test = new myModule("upstream2_11.test", upstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "upstream", "test", "2.11", "12", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val upstream2_12 = new myModule("upstream2_12", upstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream2_12")
      }
      val upstream2_12Main = new myModule("upstream2_12.main", upstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "upstream", "main", "2.12", "17", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val upstream2_12Test = new myModule("upstream2_12.test", upstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "upstream", "test", "2.12", "17", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val upstream2_13 = new myModule("upstream2_13", upstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstream2_13")
      }
      val upstream2_13Main = new myModule("upstream2_13.main", upstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "upstream", "main", "2.13", "10", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val upstream2_13Test = new myModule("upstream2_13.test", upstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "upstream", "test", "2.13", "10", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val upstreamSources = new myModule("upstream-sources", upstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/upstream")
      }
      val upstreamSourcesMain = new myModule("upstream-sources.main", upstreamGroup) {
        standardRootsForSharedModule(this, "upstream", "main")
      }
      val upstreamSourcesTest = new myModule("upstream-sources.test", upstreamGroup) {
        standardRootsForSharedModule(this, "upstream", "test")
      }

      upstreamSources.dependsOn(upstreamSourcesTest, upstreamSourcesMain)
      upstreamSourcesTest.dependsOn(upstream2_11Main)
      upstream2_11.dependsOn(upstream2_11Main, upstream2_11Test)
      upstream2_11Main.dependsOn(upstreamSourcesMain)
      upstream2_11Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_11Main)
      upstream2_12.dependsOn(upstream2_12Test, upstream2_12Main)
      upstream2_12Main.dependsOn(upstreamSourcesMain)
      upstream2_12Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_12Main)
      upstream2_13.dependsOn(upstream2_13Test, upstream2_13Main)
      upstream2_13Main.dependsOn(upstreamSourcesMain)
      upstream2_13Test.dependsOn(upstreamSourcesMain, upstreamSourcesTest, upstream2_13Main)

      val downstreamGroup = Array(projectName, "downstream")
      val downstream2_11 = new myModule("downstream2_11", downstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream2_11")
      }
      val downstream2_11Main = new myModule("downstream2_11.main", downstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "downstream", "main", "2.11", "12", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val downstream2_11Test = new myModule("downstream2_11.test", downstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "downstream", "test", "2.11", "12", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val downstream2_12 = new myModule("downstream2_12", downstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream2_12")
      }
      val downstream2_12Main = new myModule("downstream2_12.main", downstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "downstream", "main", "2.12", "17", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val downstream2_12Test = new myModule("downstream2_12.test", downstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "downstream", "test", "2.12", "17", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val downstream2_13 = new myModule("downstream2_13", downstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstream2_13")
      }
      val downstream2_13Main = new myModule("downstream2_13.main", downstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "downstream", "main", "2.13", "10", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val downstream2_13Test = new myModule("downstream2_13.test", downstreamGroup) {
        standardRootsForMatrixModulePlatform(this, "downstream", "test", "2.13", "10", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val downstreamSources = new myModule("downstream-sources", downstreamGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/downstream")
      }
      val downstreamSourcesMain = new myModule("downstream-sources.main", downstreamGroup) {
        standardRootsForSharedModule(this, "downstream", "main")
      }
      val downstreamSourcesTest = new myModule("downstream-sources.test", downstreamGroup) {
        standardRootsForSharedModule(this, "downstream", "test")
      }
      downstreamSources.dependsOn(downstreamSourcesMain, downstreamSourcesTest)
      downstreamSourcesMain.dependsOn(upstream2_11Main)
      downstreamSourcesTest.dependsOn(downstream2_11Main, upstream2_11Main)
      downstream2_11.dependsOn(downstream2_11Main, downstream2_11Test)
      downstream2_11Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_11Main)
      downstream2_11Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_11Main, upstreamSourcesMain, upstream2_11Main)
      downstream2_12.dependsOn(downstream2_12Main, downstream2_12Test)
      downstream2_12Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_12Main)
      downstream2_12Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_12Main, upstreamSourcesMain, upstream2_12Main)
      downstream2_13.dependsOn(downstream2_13Main, downstream2_13Test)
      downstream2_13Main.dependsOn(downstreamSourcesMain, upstreamSourcesMain, upstream2_13Main)
      downstream2_13Test.dependsOn(downstreamSourcesMain, downstreamSourcesTest, downstream2_13Main, upstreamSourcesMain, upstream2_13Main)


      val downstreamModules: Seq[myModule] = Seq(
        downstreamSources, downstreamSourcesMain, downstreamSourcesTest,
        downstream2_11, downstream2_11Main, downstream2_11Test,
        downstream2_12, downstream2_12Main, downstream2_12Test,
        downstream2_13, downstream2_13Main, downstream2_13Test,
      )
      val upstreamModules: Seq[myModule] = Seq(
        upstreamSources, upstreamSourcesMain, upstreamSourcesTest,
        upstream2_11, upstream2_11Main, upstream2_11Test,
        upstream2_12, upstream2_12Main, upstream2_12Test,
        upstream2_13, upstream2_13Main, upstream2_13Test
      )

      modules := Seq(sbtProjectmatrix, sbtProjectmatrixMain, sbtProjectmatrixTest, sbtProjectmatrixBuild) ++
          downstreamModules ++ upstreamModules
    }
  }
}
