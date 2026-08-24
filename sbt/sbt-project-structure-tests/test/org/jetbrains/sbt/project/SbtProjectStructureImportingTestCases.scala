package org.jetbrains.sbt.project

import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.ProjectStructureDsl.*
import org.jetbrains.sbt.project.runner.SbtProjectStructureImportingRunner
import org.jetbrains.sbt.project.structure.SbtStructureDumper
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith

import java.nio.file.Files
import java.util.UUID

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_Simple extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simple(): Unit =
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    runSimpleTest("simple", "2.13", scalaLibraries)

    // Adding some extra assertions here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    assertModulesWithScala(Seq("simple.test", "simple.main"))

    assertErrorOutputHasNotFailedProjectImport()
  end simple

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_Simple_Scala3 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simple_Scala3(): Unit =
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("3.0.2")
    runSimpleTest("simple-scala3", "3.0.2", scalaLibraries, DefaultSbtContentRootsScala3, DefaultMainSbtContentRootsScala3, DefaultTestSbtContentRootsScala3)

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_NoRootModule extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  // Test case to check whether the build module is added when the root project is skipped with ideSkipProject := true
  // TODO For now the added build module has incorrect data, fix it with SCL-25022
  def noRootModule(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    val projectName = "dummy"
    val scalaVersion = "2.13"
    runTest(
      new project(projectName) {
        libraries := scalaLibraries
        modules := Seq(
          new module(s"$projectName") {
            contentRoots += s"$getProjectPath/dummy"
            excluded := Seq("target")
          },
          new module(s"$projectName.main") {
            contentRoots := Seq(
              s"$getProjectPath/dummy/src/main",
              s"$getProjectPath/dummy/target/scala-$scalaVersion/src_managed/main",
              s"$getProjectPath/dummy/target/scala-$scalaVersion/resource_managed/main"
            )
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module(s"$projectName.test") {
            contentRoots := Seq(
              s"$getProjectPath/dummy/src/test",
              s"$getProjectPath/dummy/target/scala-$scalaVersion/src_managed/test",
              s"$getProjectPath/dummy/target/scala-$scalaVersion/resource_managed/test"
            )
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module(s"$projectName.fooModule") {
            contentRoots += s"$getProjectPath/fooModule"
            excluded := Seq("target")
          },
          new module(s"$projectName.fooModule.main") {
            contentRoots := Seq(
              s"$getProjectPath/fooModule/src/main",
              s"$getProjectPath/fooModule/target/scala-$scalaVersion/src_managed/main",
              s"$getProjectPath/fooModule/target/scala-$scalaVersion/resource_managed/main"
            )
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module(s"$projectName.fooModule.test") {
            contentRoots := Seq(
              s"$getProjectPath/fooModule/src/test",
              s"$getProjectPath/fooModule/target/scala-$scalaVersion/src_managed/test",
              s"$getProjectPath/fooModule/target/scala-$scalaVersion/resource_managed/test"
            )
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module(s"$projectName.$projectName-build") {
            contentRoots := Seq(s"$getProjectPath/dummy/project")
            sources := Nil
            excluded := Seq("project/target", "target")
          }
        )
      }
    )
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_GlobalSbtFilesFromConcurrentImports extends SbtProjectStructureImportingTestCasesUtilities:
  /*
  A stale `idea-structure*.sbt` file may hang in sbt's global plugins directory after upgrading from an older plugin
  version (which used to write such guarded files there). The current import must still succeed: the stale file is guarded
  by an `idea.import.id` that the current import never sets, so it evaluates to no settings and is not applied.
  The file intentionally declares a non-existent sbt-structure plugin version, which would fail the import if it were applied.
  */
  @Test
  def globalSbtFilesFromConcurrentImports(): Unit = {
    val settings = SbtSettings.getInstance(getMyProject)

    val tempGlobalPluginsDir = FileUtil.createTempDirectory("sbt-global-plugins-test", null)
    val updatedVmParams = settings.getVmParameters() + s" -Dsbt.global.plugins=${tempGlobalPluginsDir.toPath.toAbsolutePath}"
    settings.setVmParameters(updatedVmParams)

    val sbtFileFromOtherImport = FileUtil.createTempFile(tempGlobalPluginsDir, "idea-structure.sbt", null)
    val fileContent =
      SbtStructureDumper.createGuardedPluginContent(
        importId = UUID.randomUUID().toString,
        sbtVersion = SbtVersion("1.9.6"),
        settings = Seq(
          s"""resolvers += MavenCache("Scala Plugin Bundled Repository", file(raw"${getMyProject.getBasePath}"))""",
          s"""addSbtPlugin("org.jetbrains.scala" % "sbt-structure-extractor" % "1.121212.43243232.3232", "202232.1")"""
        )
      )
    Files.writeString(sbtFileFromOtherImport.toPath, fileContent)

    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    runSimpleTest("root", "2.13", scalaLibraries)
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_MainTestSbtModules extends SbtProjectStructureImportingTestCasesUtilities:
  /**
   * Test #SCL-23505
   */
  @Test
  def mainTestSbtModules(): Unit = {
    runTest(
      new project("root") {
        modules := Seq(
          new module("root") {
            moduleFileDirectoryPath := "mainTestSbtModules"
          },
          new module("root.main") {
            moduleFileDirectoryPath := "mainTestSbtModules"
          },
          new module("root.test~1") {
            moduleFileDirectoryPath := "mainTestSbtModules"
          },
          new module(s"root.root-build") {
            moduleFileDirectoryPath := "mainTestSbtModules"
          },
          new module("root.Main") {
            moduleFileDirectoryPath := "mainTestSbtModules/Main"
          },
          new module("root.Main.main") {
            moduleFileDirectoryPath := "mainTestSbtModules/Main"
          },
          new module("root.Main.test") {
            moduleFileDirectoryPath := "mainTestSbtModules/Main"
          },
          new module("root.test") {
            moduleFileDirectoryPath := "mainTestSbtModules/test"
          },
          new module("root.test.main") {
            moduleFileDirectoryPath := "mainTestSbtModules/test"
          },
          new module("root.test.test") {
            moduleFileDirectoryPath := "mainTestSbtModules/test"
          },
        )
      }
    )
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_MultiModule extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def multiModule(): Unit = runTest(
    new project("multiModule") {
      lazy val foo = new module("multiModule.foo") {
        moduleDependencies ++= Seq(
          new dependency(fooMain) {
            isExported := false
          },
          new dependency(fooTest) {
            isExported := false
          }
        )
      }
      lazy val fooMain: module = new module("multiModule.foo.main") {
        moduleDependencies += new dependency(barMain) {
          isExported := false
        }
      }
      lazy val fooTest: module = new module("multiModule.foo.test"){
        moduleDependencies ++= Seq(
          new dependency(fooMain) { isExported := false },
          new dependency(barMain) { isExported := false }
        )
      }

      lazy val bar  = new module("multiModule.bar")
      lazy val barMain  = new module("multiModule.bar.main")
      lazy val barTest  = new module("multiModule.bar.test")
      lazy val root = new module("multiModule")
      lazy val rootMain = new module("multiModule.main")
      lazy val rootTest = new module("multiModule.test")

      modules := Seq(
        root, rootMain, rootTest,
        foo, fooMain, fooTest,
        bar, barMain, barTest
      )
    })

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_NumberSuffixDeduplicationStrategy extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def numberSuffixDeduplicationStrategy():Unit = runTest(
    new project("root") {
      val root: module = new module("root")
      val rootMain: module = new module("root.main")
      val rootTest: module = new module("root.test")

      val foo: module = new module("root.foo_")
      val fooMain: module = new module("root.foo_.main")
      val fooTest: module = new module("root.foo_.test")

      val fooDuplication: module = new module("root.foo_~1")
      val fooDuplicationMain: module = new module("root.foo_~1.main")
      val fooDuplicationTest: module = new module("root.foo_~1.test")

      val rootBuildModule: module = new module("root.root-build") { moduleFileDirectoryPath := "numberSuffixDeduplicationStrategy" }

      modules := Seq(
        root, rootMain, rootTest,
        foo, fooMain, fooTest,
        fooDuplication, fooDuplicationMain, fooDuplicationTest,
        rootBuildModule,
      )
    }
  )

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_LibraryDependenciesOrder extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def libraryDependenciesOrder(): Unit = {
    val expectedProject: project = new project("libraryDependenciesOrder") {
      val scalaLibraries: Seq[dependency[library]] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14").map { library =>
        new dependency(library) { scope := DependencyScope.COMPILE }
      }

      lazy val coreMain: module = new module("libraryDependenciesOrder.core.main") {
        moduleDependencies ++= Seq()
        libraryDependencies := scalaLibraries ++ Seq(
          new dependency(new library(s"sbt: org.typelevel:cats-core_2.13:2.10.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.typelevel:cats-kernel_2.13:2.10.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: javax.servlet:javax.servlet-api:4.0.1:jar")) { scope := DependencyScope.RUNTIME },
        )
      }
      lazy val coreTest: module = new module("libraryDependenciesOrder.core.test") {
        moduleDependencies ++= Seq(
          new dependency(coreMain) { isExported := false },
        )
        libraryDependencies := scalaLibraries ++ Seq(
          new dependency(new library(s"sbt: org.typelevel:cats-core_2.13:2.10.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: javax.servlet:javax.servlet-api:4.0.1:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.scalameta:munit_2.13:1.0.0-M9:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.typelevel:cats-kernel_2.13:2.10.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.scalameta:junit-interface:1.0.0-M9:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: junit:junit:4.13.2:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.scala-sbt:test-interface:1.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.hamcrest:hamcrest-core:1.3:jar")) { scope := DependencyScope.COMPILE },
        )
      }
      lazy val core: module = new module("libraryDependenciesOrder.core") {
        moduleDependencies ++= Seq(
          new dependency(coreMain) { isExported := false },
          new dependency(coreTest) { isExported := false }
        )
        libraryDependencies := Seq()
      }

      lazy val apiMain: module = new module("libraryDependenciesOrder.api.main") {
        moduleDependencies ++= Seq(
          new dependency(coreMain) {
            isExported := false
            scope := DependencyScope.RUNTIME
          },
        )
        libraryDependencies := scalaLibraries ++ Seq(
          new dependency(new library(s"sbt: com.typesafe.akka:akka-http_2.13:10.4.0:jar")) { scope := DependencyScope.PROVIDED },
          new dependency(new library(s"sbt: com.typesafe.akka:akka-http-core_2.13:10.4.0:jar")) { scope := DependencyScope.PROVIDED },
          new dependency(new library(s"sbt: com.typesafe.akka:akka-parsing_2.13:10.4.0:jar")) { scope := DependencyScope.PROVIDED },
          new dependency(new library(s"sbt: org.typelevel:cats-core_2.13:2.10.0:jar")) { scope := DependencyScope.RUNTIME },
          new dependency(new library(s"sbt: javax.servlet:javax.servlet-api:4.0.1:jar")) { scope := DependencyScope.RUNTIME },
          new dependency(new library(s"sbt: org.typelevel:cats-kernel_2.13:2.10.0:jar")) { scope := DependencyScope.RUNTIME },
        )
      }
      lazy val apiTest: module = new module("libraryDependenciesOrder.api.test") {
        moduleDependencies ++= Seq(
          new dependency(apiMain) { isExported := false },
          new dependency(coreMain) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
        )
        libraryDependencies := scalaLibraries ++ Seq(
          new dependency(new library(s"sbt: com.typesafe.akka:akka-http_2.13:10.4.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.typelevel:cats-core_2.13:2.10.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: javax.servlet:javax.servlet-api:4.0.1:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: com.typesafe.akka:akka-http-core_2.13:10.4.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.typelevel:cats-kernel_2.13:2.10.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: com.typesafe.akka:akka-parsing_2.13:10.4.0:jar")) { scope := DependencyScope.COMPILE },
        )
      }
      lazy val api: module = new module("libraryDependenciesOrder.api") {
        moduleDependencies ++= Seq(
          new dependency(apiMain) { isExported := false },
          new dependency(apiTest) { isExported := false }
        )
        libraryDependencies := Seq()
      }

      lazy val serviceMain: module = new module("libraryDependenciesOrder.service.main") {
        moduleDependencies ++= Seq()
        libraryDependencies := scalaLibraries
      }
      lazy val serviceTest: module = new module("libraryDependenciesOrder.service.test") {
        moduleDependencies ++= Seq(
          new dependency(serviceMain) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
          new dependency(apiTest) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
          new dependency(apiMain) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
          new dependency(coreMain) {
            isExported := false
            scope := DependencyScope.COMPILE
          }
        )
        libraryDependencies := scalaLibraries ++ Seq(
          new dependency(new library(s"sbt: ch.qos.logback:logback-classic:1.4.9:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: ch.qos.logback:logback-core:1.4.9:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.slf4j:slf4j-api:2.0.7:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.typelevel:cats-core_2.13:2.10.0:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: javax.servlet:javax.servlet-api:4.0.1:jar")) { scope := DependencyScope.COMPILE },
          new dependency(new library(s"sbt: org.typelevel:cats-kernel_2.13:2.10.0:jar")) { scope := DependencyScope.COMPILE },
        )
      }
      lazy val service: module = new module("libraryDependenciesOrder.service") {
        moduleDependencies ++= Seq(
          new dependency(serviceMain) { isExported := false },
          new dependency(serviceTest) { isExported := false }
        )
        libraryDependencies := Seq()
      }

      lazy val rootMain: module = new module("libraryDependenciesOrder.main") {
        moduleDependencies ++= Seq()
        libraryDependencies := scalaLibraries
      }
      lazy val rootTest: module = new module("libraryDependenciesOrder.test") {
        moduleDependencies ++= Seq(
          new dependency(rootMain) { isExported := false }
        )
        libraryDependencies := scalaLibraries
      }
      lazy val root: module = new module("libraryDependenciesOrder") {
        moduleDependencies ++= Seq(
          new dependency(rootMain) { isExported := false },
          new dependency(rootTest) { isExported := false }
        )
        libraryDependencies := Seq()
      }

      modules := Seq(
        root, rootMain, rootTest,
        core, coreMain, coreTest,
        api, apiMain, apiTest,
        service, serviceMain, serviceTest
      )
    }
    runTest(expectedProject, _.copy(checkLibraryDependenciesOrder = true))
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleSbt013 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt013(): Unit =
    simpleSbtIvyBasedTest(mutedNotificationTitles = Seq("Legacy sbt version 0.13.18 detected"))

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleSbt104 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt104(): Unit =
    simpleSbtIvyBasedTest()

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleSbt116 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt116(): Unit =
    simpleSbtIvyBasedTest()

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleSbt128 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt128(): Unit =
    simpleSbtIvyBasedTest()

abstract class SbtProjectStructureImportingTestCasesUtilities extends SbtProjectStructureImportingTestCase:
  // Duplicated code from SbtProjectStructureImportingSuiteBase. Will be removed after all test cases have been migrated
  // to this new running scheme.
  protected def simpleSbtIvyBasedTest(mutedNotificationTitles: Seq[String] = Seq.empty): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkFromIvy(useEnv = true)("2.12.10")

    runSimpleTest("simple", "2.12", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = customSbtContentRootsForParent(12),
      expectedSbtCompletionVariantsForMainModule = customSbtContentRootsForMain(12),
      expectedSbtCompletionVariantsForTestModule = customSbtContentRootsForTest(12),
      mutedNotificationTitles = mutedNotificationTitles
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      getMyProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.getProcessOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  protected def runSimpleTest(
    projectName: String,
    scalaVersion: String,
    expectedScalaLibraries: Seq[library],
    expectedSbtCompletionVariantsForParentModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultSbtContentRootsScala213,
    expectedSbtCompletionVariantsForMainModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultMainSbtContentRootsScala213,
    expectedSbtCompletionVariantsForTestModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultTestSbtContentRootsScala213,
    mutedNotificationTitles: Seq[String] = Seq.empty
  ): Unit = {
    runTest(
      new project(projectName) {
        libraries := expectedScalaLibraries

        modules := Seq(
          new module(projectName) {
            contentRoots += getProjectPath
            excluded := Seq("target")
          },
          new module(s"$projectName.main") {
            contentRoots := Seq(s"$getProjectPath/src/main", s"$getProjectPath/target/scala-$scalaVersion/src_managed/main", s"$getProjectPath/target/scala-$scalaVersion/resource_managed/main")
            sources := Seq("scala", "java")
            resources := Seq("resources")
            testSources := Nil
            testResources := Nil
            libraryDependencies := expectedScalaLibraries
          },
          new module(s"$projectName.test") {
            contentRoots := Seq(s"$getProjectPath/src/test", s"$getProjectPath/target/scala-$scalaVersion/src_managed/test", s"$getProjectPath/target/scala-$scalaVersion/resource_managed/test")
            sources := Nil
            resources := Nil
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
          },
          new module(s"$projectName.$projectName-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          }
        )
      },
      identity,
      mutedNotificationTitles = mutedNotificationTitles
    )
    assertDirectoryCompletionVariantsForProjectPaths(
      expectedSbtCompletionVariantsForParentModule,
      expectedSbtCompletionVariantsForMainModule,
      expectedSbtCompletionVariantsForTestModule,
      getMyProject.baseDir.getPath
    )
  }

  protected def assertModulesWithScala(expectedModuleNames: Seq[String]): Unit = {
    assertEquals(
      "`modulesWithScala` should return list of non *-build modules",
      expectedModuleNames,
      getMyProject.modulesWithScala.map(_.getName),
    )
  }

  protected def assertErrorOutputHasNotFailedProjectImport(): Unit = {
    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.getProcessOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  private def customSbtContentRootsForParent(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("src/main/java", JavaSourceRootType.SOURCE),
      ("src/main/scala", JavaSourceRootType.SOURCE),
      (s"src/main/scala-2.$binaryVersion", JavaSourceRootType.SOURCE),
      ("src/test/java", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
      (s"src/test/scala-2.$binaryVersion", JavaSourceRootType.TEST_SOURCE),
      ("src/main/resources", JavaResourceRootType.RESOURCE),
      ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  private def customSbtContentRootsForMain(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.SOURCE),
      ("scala", JavaSourceRootType.SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.SOURCE),
      ("resources", JavaResourceRootType.RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  private def customSbtContentRootsForTest(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.TEST_SOURCE),
      ("scala", JavaSourceRootType.TEST_SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.TEST_SOURCE),
      ("resources", JavaResourceRootType.TEST_RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)
