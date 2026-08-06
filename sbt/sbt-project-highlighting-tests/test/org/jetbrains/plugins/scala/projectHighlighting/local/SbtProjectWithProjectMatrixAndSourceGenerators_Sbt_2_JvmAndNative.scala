package org.jetbrains.plugins.scala.projectHighlighting.local

import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener

/**
 * Verifies sbt 2 project matrix highlighting/import structure for a mixed JVM-only and JVM+Native matrix setup.
 *
 * Core difference from [[SbtProjectWithProjectMatrixAndSourceGenerators_Sbt_2_Jvm]]:
 * this test also asserts Native platform modules, platform-specific roots, and cross-platform dependencies.
 */
class SbtProjectWithProjectMatrixAndSourceGenerators_Sbt_2_JvmAndNative
  extends SbtProjectWithProjectMatrixAndSourceGenerators_Sbt_2_TestBase {

  override def projectName = "sbt-projectmatrix-with-source-generators-sbt2-jvm-native"

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

      val upstreamBothPlatformsGroup = Array(projectName, "upstreamBothPlatforms")
      val upstreamBothPlatforms2_11 = new myModule("upstreamBothPlatforms2_11", upstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatforms2_11")
      }
      val upstreamBothPlatforms2_11Main = new myModule("upstreamBothPlatforms2_11.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "main", "2.11", "12", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val upstreamBothPlatforms2_11Test = new myModule("upstreamBothPlatforms2_11.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "test", "2.11", "12", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val upstreamBothPlatforms2_12 = new myModule("upstreamBothPlatforms2_12", upstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatforms2_12")
      }
      val upstreamBothPlatforms2_12Main = new myModule("upstreamBothPlatforms2_12.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "main", "2.12", "17", "jvm")
      }
      val upstreamBothPlatforms2_12Test = new myModule("upstreamBothPlatforms2_12.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "test", "2.12", "17", "jvm")
      }
      val upstreamBothPlatforms2_13 = new myModule("upstreamBothPlatforms2_13", upstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatforms2_13")
      }
      val upstreamBothPlatforms2_13Main = new myModule("upstreamBothPlatforms2_13.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "main", "2.13", "10", "jvm")
      }
      val upstreamBothPlatforms2_13Test = new myModule("upstreamBothPlatforms2_13.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "test", "2.13", "10", "jvm")
      }
      val upstreamBothPlatformsNative2_12 = new myModule("upstreamBothPlatformsNative2_12", upstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatformsNative2_12")
      }
      val upstreamBothPlatformsNative2_12Main = new myModule("upstreamBothPlatformsNative2_12.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "main", "2.12", "17", "native")
      }
      val upstreamBothPlatformsNative2_12Test = new myModule("upstreamBothPlatformsNative2_12.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "test", "2.12", "17", "native")
      }
      val upstreamBothPlatformsNative2_13 = new myModule("upstreamBothPlatformsNative2_13", upstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/upstreamBothPlatformsNative2_13")
      }
      val upstreamBothPlatformsNative2_13Main = new myModule("upstreamBothPlatformsNative2_13.main", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "main", "2.13", "10", "native")
      }
      val upstreamBothPlatformsNative2_13Test = new myModule("upstreamBothPlatformsNative2_13.test", upstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "upstreamBothPlatforms", "test", "2.13", "10", "native")
      }
      val upstreamBothPlatformsSources = new myModule("upstreamBothPlatforms-sources", upstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/upstreamBothPlatforms")
      }
      val upstreamBothPlatformsSourcesMain = new myModule("upstreamBothPlatforms-sources.main", upstreamBothPlatformsGroup) {
        standardRootsForSharedMultiPlatformModule(this, "upstreamBothPlatforms", "main")
      }
      val upstreamBothPlatformsSourcesTest = new myModule("upstreamBothPlatforms-sources.test", upstreamBothPlatformsGroup) {
        standardRootsForSharedMultiPlatformModule(this, "upstreamBothPlatforms", "test")
      }

      upstreamBothPlatformsSources.dependsOn(upstreamBothPlatformsSourcesTest, upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsSourcesTest.dependsOn(upstreamBothPlatforms2_11Main)
      upstreamBothPlatforms2_11.dependsOn(upstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Test)
      upstreamBothPlatforms2_11Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatforms2_11Test.dependsOn(upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest, upstreamBothPlatforms2_11Main)
      upstreamBothPlatforms2_12.dependsOn(upstreamBothPlatforms2_12Main, upstreamBothPlatforms2_12Test)
      upstreamBothPlatforms2_12Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatforms2_12Test.dependsOn(upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest, upstreamBothPlatforms2_12Main)
      upstreamBothPlatforms2_13.dependsOn(upstreamBothPlatforms2_13Main, upstreamBothPlatforms2_13Test)
      upstreamBothPlatforms2_13Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatforms2_13Test.dependsOn(upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest, upstreamBothPlatforms2_13Main)
      upstreamBothPlatformsNative2_12.dependsOn(upstreamBothPlatformsNative2_12Main, upstreamBothPlatformsNative2_12Test)
      upstreamBothPlatformsNative2_12Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsNative2_12Test.dependsOn(upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest, upstreamBothPlatformsNative2_12Main)
      upstreamBothPlatformsNative2_13.dependsOn(upstreamBothPlatformsNative2_13Main, upstreamBothPlatformsNative2_13Test)
      upstreamBothPlatformsNative2_13Main.dependsOn(upstreamBothPlatformsSourcesMain)
      upstreamBothPlatformsNative2_13Test.dependsOn(upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest, upstreamBothPlatformsNative2_13Main)

      val downstreamBothPlatformsGroup = Array(projectName, "downstreamBothPlatforms")
      val downstreamBothPlatforms2_11 = new myModule("downstreamBothPlatforms2_11", downstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatforms2_11")
      }
      val downstreamBothPlatforms2_11Main = new myModule("downstreamBothPlatforms2_11.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "main", "2.11", "12", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val downstreamBothPlatforms2_11Test = new myModule("downstreamBothPlatforms2_11.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "test", "2.11", "12", "jvm", includeLegacyScalaVersionRoot = true)
      }
      val downstreamBothPlatforms2_12 = new myModule("downstreamBothPlatforms2_12", downstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatforms2_12")
      }
      val downstreamBothPlatforms2_12Main = new myModule("downstreamBothPlatforms2_12.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "main", "2.12", "17", "jvm")
      }
      val downstreamBothPlatforms2_12Test = new myModule("downstreamBothPlatforms2_12.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "test", "2.12", "17", "jvm")
      }
      val downstreamBothPlatforms2_13 = new myModule("downstreamBothPlatforms2_13", downstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatforms2_13")
      }
      val downstreamBothPlatforms2_13Main = new myModule("downstreamBothPlatforms2_13.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "main", "2.13", "10", "jvm")
      }
      val downstreamBothPlatforms2_13Test = new myModule("downstreamBothPlatforms2_13.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "test", "2.13", "10", "jvm")
      }
      val downstreamBothPlatformsNative2_12 = new myModule("downstreamBothPlatformsNative2_12", downstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatformsNative2_12")
      }
      val downstreamBothPlatformsNative2_12Main = new myModule("downstreamBothPlatformsNative2_12.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "main", "2.12", "17", "native")
      }
      val downstreamBothPlatformsNative2_12Test = new myModule("downstreamBothPlatformsNative2_12.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "test", "2.12", "17", "native")
      }
      val downstreamBothPlatformsNative2_13 = new myModule("downstreamBothPlatformsNative2_13", downstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/.sbt/matrix/downstreamBothPlatformsNative2_13")
      }
      val downstreamBothPlatformsNative2_13Main = new myModule("downstreamBothPlatformsNative2_13.main", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "main", "2.13", "10", "native")
      }
      val downstreamBothPlatformsNative2_13Test = new myModule("downstreamBothPlatformsNative2_13.test", downstreamBothPlatformsGroup) {
        standardRootsForMatrixModulePlatform(this, "downstreamBothPlatforms", "test", "2.13", "10", "native")
      }
      val downstreamBothPlatformsSources = new myModule("downstreamBothPlatforms-sources", downstreamBothPlatformsGroup, excludeTargetDir = true) {
        contentRoots := Seq("%PROJECT_ROOT%/downstreamBothPlatforms")
      }
      val downstreamBothPlatformsSourcesMain = new myModule("downstreamBothPlatforms-sources.main", downstreamBothPlatformsGroup) {
        standardRootsForSharedMultiPlatformModule(this, "downstreamBothPlatforms", "main")
      }
      val downstreamBothPlatformsSourcesTest = new myModule("downstreamBothPlatforms-sources.test", downstreamBothPlatformsGroup) {
        standardRootsForSharedMultiPlatformModule(this, "downstreamBothPlatforms", "test")
      }

      downstreamBothPlatformsSources.dependsOn(downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest)
      downstreamBothPlatformsSourcesMain.dependsOn(upstreamBothPlatforms2_11Main)
      downstreamBothPlatformsSourcesTest.dependsOn(downstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Main)
      downstreamBothPlatforms2_11.dependsOn(downstreamBothPlatforms2_11Main, downstreamBothPlatforms2_11Test)
      downstreamBothPlatforms2_11Main.dependsOn(downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain, upstreamBothPlatforms2_11Main)
      downstreamBothPlatforms2_11Test.dependsOn(downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest, downstreamBothPlatforms2_11Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatforms2_11Main)
      downstreamBothPlatforms2_12.dependsOn(downstreamBothPlatforms2_12Main, downstreamBothPlatforms2_12Test)
      downstreamBothPlatforms2_12Main.dependsOn(downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain, upstreamBothPlatforms2_12Main)
      downstreamBothPlatforms2_12Test.dependsOn(downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest, downstreamBothPlatforms2_12Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatforms2_12Main)
      downstreamBothPlatforms2_13.dependsOn(downstreamBothPlatforms2_13Main, downstreamBothPlatforms2_13Test)
      downstreamBothPlatforms2_13Main.dependsOn(downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain, upstreamBothPlatforms2_13Main)
      downstreamBothPlatforms2_13Test.dependsOn(downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest, downstreamBothPlatforms2_13Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatforms2_13Main)
      downstreamBothPlatformsNative2_12.dependsOn(downstreamBothPlatformsNative2_12Main, downstreamBothPlatformsNative2_12Test)
      downstreamBothPlatformsNative2_12Main.dependsOn(downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsNative2_12Main)
      downstreamBothPlatformsNative2_12Test.dependsOn(downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsNative2_12Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsNative2_12Main)
      downstreamBothPlatformsNative2_13.dependsOn(downstreamBothPlatformsNative2_13Main, downstreamBothPlatformsNative2_13Test)
      downstreamBothPlatformsNative2_13Main.dependsOn(downstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsNative2_13Main)
      downstreamBothPlatformsNative2_13Test.dependsOn(downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest, downstreamBothPlatformsNative2_13Main, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsNative2_13Main)

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
        upstream2_13, upstream2_13Main, upstream2_13Test,
      )
      val downstreamBothPlatformsModules: Seq[myModule] = Seq(
        downstreamBothPlatformsSources, downstreamBothPlatformsSourcesMain, downstreamBothPlatformsSourcesTest,
        downstreamBothPlatforms2_11, downstreamBothPlatforms2_11Main, downstreamBothPlatforms2_11Test,
        downstreamBothPlatforms2_12, downstreamBothPlatforms2_12Main, downstreamBothPlatforms2_12Test,
        downstreamBothPlatforms2_13, downstreamBothPlatforms2_13Main, downstreamBothPlatforms2_13Test,
        downstreamBothPlatformsNative2_12, downstreamBothPlatformsNative2_12Main, downstreamBothPlatformsNative2_12Test,
        downstreamBothPlatformsNative2_13, downstreamBothPlatformsNative2_13Main, downstreamBothPlatformsNative2_13Test,
      )
      val upstreamBothPlatformsModules: Seq[myModule] = Seq(
        upstreamBothPlatformsSources, upstreamBothPlatformsSourcesMain, upstreamBothPlatformsSourcesTest,
        upstreamBothPlatforms2_11, upstreamBothPlatforms2_11Main, upstreamBothPlatforms2_11Test,
        upstreamBothPlatforms2_12, upstreamBothPlatforms2_12Main, upstreamBothPlatforms2_12Test,
        upstreamBothPlatforms2_13, upstreamBothPlatforms2_13Main, upstreamBothPlatforms2_13Test,
        upstreamBothPlatformsNative2_12, upstreamBothPlatformsNative2_12Main, upstreamBothPlatformsNative2_12Test,
        upstreamBothPlatformsNative2_13, upstreamBothPlatformsNative2_13Main, upstreamBothPlatformsNative2_13Test,
      )

      modules := Seq(sbtProjectmatrix, sbtProjectmatrixMain, sbtProjectmatrixTest, sbtProjectmatrixBuild) ++
        downstreamModules ++ upstreamModules ++ downstreamBothPlatformsModules ++ upstreamBothPlatformsModules
    }
  }
}
