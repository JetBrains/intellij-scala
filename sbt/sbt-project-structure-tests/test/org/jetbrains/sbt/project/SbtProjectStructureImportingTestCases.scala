package org.jetbrains.sbt.project

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.external.JdkByName
import org.jetbrains.sbt.project.ProjectStructureDsl.*
import org.jetbrains.sbt.project.ProjectStructureTestUtils.expectedScalaSdkLibraryFromCoursier
import org.jetbrains.sbt.project.runner.SbtProjectStructureImportingRunner
import org.jetbrains.sbt.project.structure.SbtStructureDumper
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.{Sbt, SbtVersion}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith

import java.net.URI
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
class SbtProjectStructureImportingTestCase_UnmanagedDependency extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def unmanagedDependency(): Unit = runTest(
    new project("unmanagedDependency") {
      val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
      val managedLibrary: library = new library("sbt: org.apache.commons:commons-compress:1.21:jar")
      libraries := scalaLibraries :+ managedLibrary

      lazy val unmanagedLibrary: library = new library(s"sbt: ${Sbt.UnmanagedLibraryName}") {
        libClasses += (getTestProjectPath / "lib" / "unmanaged.jar").toAbsolutePath.toString
      }
      val myLibraryDependencies: Seq[library] = unmanagedLibrary +: managedLibrary +: scalaLibraries

      def createSourceModule(name: String): module = new module(s"unmanagedDependency.$name") {
        libraries := Seq(unmanagedLibrary)
        libraryDependencies := myLibraryDependencies
      }

      val unmanagedDependency = new module("unmanagedDependency")
      val unmanagedDependencyMain: module = createSourceModule("main")
      val unmanagedDependencyTest: module = createSourceModule("test")

      modules := Seq(
        unmanagedDependency, unmanagedDependencyMain, unmanagedDependencyTest
      )
    }
  )

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SbtIdeSettingsRespectIdeExcludedDirectoriesSetting extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def sbtIdeSettingsRespectIdeExcludedDirectoriesSetting(): Unit = runTest(
    new project("root") {
      lazy val root: module = new module("root") {
        excluded := Seq(
          "directory-to-exclude-1",
          "directory/to/exclude/2"
        )
      }
      lazy val rootMain: module = new module("root.main") {
        excluded := Seq()
      }
      lazy val rootTest: module = new module("root.test") {
        excluded := Seq()
      }
      modules := Seq(root, rootMain, rootTest)
    }
  )

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SCL14635 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def SCL14635(): Unit = runTest(
    new project("SCL-14635") {
      private val buildURI: URI = getTestProjectPath.toCanonicalPath.toUri

      private val sbtIdeaPluginGroup = Array("sbtIdeaPlugin")
      private val sbtIdeSettingsGroup = Array("sbt-ide-settings")

      val url1 = "https://github.com/JetBrains/sbt-ide-settings.git"
      val url2 = "https://github.com/JetBrains/sbt-idea-plugin.git#v4.0.3"

      modules := Seq(
        new module("SCL-14635") {
          sbtBuildURI := buildURI
          sbtProjectId := "root"
        },
        new module("SCL-14635.main") {
          sbtBuildURI := buildURI
          sbtProjectId := "root"
        },
        new module("SCL-14635.test") {
          sbtBuildURI := buildURI
          sbtProjectId := "root"
        },
        new module("SCL-14635-build", Array("SCL-14635")),

        // NOTE: sbtIdeaPlugin also has inner module named `sbt-idea-plugin` (with dashes), but it's separate, non-root module
        new module("sbtIdeaPlugin") {
          sbtBuildURI := new URI(url2)
          sbtProjectId := "sbtIdeaPlugin"
        },
        new module("sbtIdeaPlugin.main") {
          sbtBuildURI := new URI(url2)
          sbtProjectId := "sbtIdeaPlugin"
        },
        new module("sbtIdeaPlugin.test") {
          sbtBuildURI := new URI(url2)
          sbtProjectId := "sbtIdeaPlugin"
        },
        new module("sbtIdeaPlugin-build", sbtIdeaPluginGroup),

        new module("sbt-ide-settings") {
          sbtBuildURI := new URI(url1)
          sbtProjectId := "root"
        },
        new module("sbt-ide-settings.main") {
          sbtBuildURI := new URI(url1)
          sbtProjectId := "root"
        },
        new module("sbt-ide-settings.test") {
          sbtBuildURI := new URI(url1)
          sbtProjectId := "root"
        },
        new module("sbt-ide-settings-build", sbtIdeSettingsGroup)
      ) ++
        createModuleWithSourceSet("sbt-idea-plugin", sbtIdeaPluginGroup) ++
        createModuleWithSourceSet("sbt-declarative-core", sbtIdeaPluginGroup) ++
        createModuleWithSourceSet("sbt-declarative-packaging", sbtIdeaPluginGroup) ++
        createModuleWithSourceSet("sbt-declarative-visualizer", sbtIdeaPluginGroup)
    }
  )

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_NonSourceConfigurationsWithNestedProjectDependencies extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def nonSourceConfigurationsWithNestedProjectDependencies():Unit = {
    val projectName = "nonSourceConfigurationsWithNestedProjectDependencies"
    runTest(
      new project(projectName) {

        lazy val proj0: module = new module(s"$projectName.proj0") {
          sbtProjectId := "proj0"
          moduleDependencies ++= Seq(
            new dependency(proj0Main) {
              isExported := false
            },
            new dependency(proj0Test) {
              isExported := false
            }
          )
        }
        lazy val proj0Main: module = new module(s"$projectName.proj0.main") {
          sbtProjectId := "proj0"
          moduleDependencies := Seq()
        }
        lazy val proj0Test: module = new module(s"$projectName.proj0.test") {
          sbtProjectId := "proj0"
          moduleDependencies := Seq(
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }

        lazy val proj1: module = new module(s"$projectName.proj1") {
          sbtProjectId := "proj1"
          moduleDependencies ++= Seq(
            new dependency(proj1Main) {
              isExported := false
            },
            new dependency(proj1Test) {
              isExported := false
            }
          )
        }
        lazy val proj1Main: module = new module(s"$projectName.proj1.main") {
          sbtProjectId := "proj1"
          moduleDependencies := Seq()
        }
        lazy val proj1Test: module = new module(s"$projectName.proj1.test") {
          sbtProjectId := "proj1"
          moduleDependencies := Seq(
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }

        lazy val proj2: module = new module(s"$projectName.proj2") {
          sbtProjectId := "proj2"
          moduleDependencies ++= Seq(
            new dependency(proj2Main) {
              isExported := false
            },
            new dependency(proj2Test) {
              isExported := false
            }
          )
        }

        lazy val proj2Main: module = new module(s"$projectName.proj2.main") {
          sbtProjectId := "proj2"
          moduleDependencies := Seq(
            new dependency(proj1Test) {
              isExported := false
              scope := DependencyScope.PROVIDED
            },
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.PROVIDED
            },
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.PROVIDED
            },
          )
        }

        lazy val proj2Test: module = new module(s"$projectName.proj2.test") {
          sbtProjectId := "proj2"
          moduleDependencies := Seq(
            new dependency(proj2Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Test) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }

        lazy val proj3: module = new module(s"$projectName.proj3") {
          sbtProjectId := "proj3"
          moduleDependencies ++= Seq(
            new dependency(proj3Main) {
              isExported := false
            },
            new dependency(proj3Test) {
              isExported := false
            }
          )
        }

        lazy val proj3Main: module = new module(s"$projectName.proj3.main") {
          sbtProjectId := "proj3"
          moduleDependencies := Seq(
            new dependency(proj1Test) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
          )
        }

        lazy val proj3Test: module = new module(s"$projectName.proj3.test") {
          sbtProjectId := "proj3"
          moduleDependencies := Seq(
            new dependency(proj3Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Test) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }

        lazy val root: module = new module(projectName) {
          sbtProjectId := "root"
          moduleDependencies ++= Seq(
            new dependency(rootMain) {
              isExported := false
            },
            new dependency(rootTest) {
              isExported := false
            }
          )
        }
        lazy val rootMain: module = new module(s"$projectName.main") {
          sbtProjectId := "root"
          moduleDependencies := Seq()
        }
        lazy val rootTest: module = new module(s"$projectName.test") {
          sbtProjectId := "root"
          moduleDependencies := Seq(
            new dependency(rootMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj2Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }
        modules := Seq(
          root, rootMain, rootTest,
          proj0, proj0Main, proj0Test,
          proj1, proj1Main, proj1Test,
          proj2, proj2Main, proj2Test,
          proj3, proj3Main, proj3Test
        )
      }
    )
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

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleSbt1313 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt1313(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")

    runSimpleTest("simple", "2.13", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = customSbtContentRootsForParent(13),
      expectedSbtCompletionVariantsForMainModule = customSbtContentRootsForMain(13),
      expectedSbtCompletionVariantsForTestModule = customSbtContentRootsForTest(13)
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

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleSbt149 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def simpleSbt149(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")

    runSimpleTest("simple", "2.13", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = DefaultSbtContentRootsScala213,
      expectedSbtCompletionVariantsForMainModule = DefaultMainSbtContentRootsScala213,
      expectedSbtCompletionVariantsForTestModule = DefaultTestSbtContentRootsScala213
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

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SCL13600 extends SbtProjectStructureImportingTestCasesUtilities:
  /**
   * SCL-13600: generate all modules when there is a duplicate project id in the sbt build
   * due to references to different builds, or multiple sbt projects being imported independently from IDEA
   */
  @Test
  def SCL13600(): Unit = runTest(
    new project("root") {
      val buildURI: URI = getTestProjectPath.toCanonicalPath.toUri

      val rootC1: module = new module("Build C1 Name") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies ++= Seq(
          new dependency(rootC1Main) {
            isExported := false
          },
          new dependency(rootC1Test) {
            isExported := false
          }
        )
      }
      lazy val rootC1Main: module = new module("Build C1 Name.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq()
      }
      lazy val rootC1Test: module = new module("Build C1 Name.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq(rootC1Main)
      }
      val rootC2: module = new module("Build C2 Name") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c2/")
        moduleDependencies ++= Seq(
          new dependency(rootC2Main) {
            isExported := false
          },
          new dependency(rootC2Test) {
            isExported := false
          }
        )
      }
      lazy val rootC2Main: module = new module("Build C2 Name.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c2/")
        moduleDependencies := Seq()
      }
      lazy val rootC2Test: module = new module("Build C2 Name.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c2/")
        moduleDependencies := Seq(rootC2Main)
      }
      val rootC3: module = new module("suffix2.root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies ++= Seq(
          new dependency(rootC3Main) {
            isExported := false
          },
          new dependency(rootC3Test) {
            isExported := false
          }
        )
      }
      lazy val rootC3Main: module = new module("suffix2.root.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      lazy val rootC3Test: module = new module("suffix2.root.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies := Seq(rootC3Main)
      }
      val rootC4: module = new module("suffix1.suffix2.root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies ++= Seq(
          new dependency(rootC4Main) {
            isExported := false
          },
          new dependency(rootC4Test) {
            isExported := false
          }
        )
      }
      lazy val rootC4Main: module = new module("suffix1.suffix2.root.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      lazy val rootC4Test: module = new module("suffix1.suffix2.root.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies := Seq(rootC4Main)
      }
      val root: module = new module("root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies ++= Seq(
          new dependency(rootMain) {
            isExported := false
          },
          new dependency(rootTest) {
            isExported := false
          }
        )
      }
      lazy val rootMain: module = new module("root.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies := Seq(
          new dependency(rootC1Main) {isExported := false },
          new dependency(rootC2Main) {isExported := false },
          new dependency(rootC3Main) {isExported := false },
          new dependency(rootC4Main) {isExported := false },
        )
      }
      lazy val rootTest: module = new module("root.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies := Seq(
          new dependency(rootMain) {isExported := false },
          new dependency(rootC1Main) {isExported := false },
          new dependency(rootC2Main) {isExported := false },
          new dependency(rootC3Main) {isExported := false },
          new dependency(rootC4Main) {isExported := false },
        )
      }

      val modulesFromRoot: Seq[module] =
        createModuleWithSourceSet("project1InRootBuild", Array("root")) ++
          createModuleWithSourceSet("project2InRootBuild", Array("root")) ++
          createModuleWithSourceSet("project3InRootBuildWithSameName", Array("root", "same name in root build")) ++
          createModuleWithSourceSet("project4InRootBuildWithSameName", Array("root", "same name in root build")) ++
          createModuleWithSourceSet("project5InRootBuildWithSameGlobalName", Array("root", "same global name")) ++
          createModuleWithSourceSet("project6InRootBuildWithSameGlobalName", Array("root", "same global name"))

      val modulesFromC1: Seq[module] =
        Seq(rootC1,rootC1Main, rootC1Test) ++
          createModuleWithSourceSet("project1InC1", Array("Build C1 Name")) ++
          createModuleWithSourceSet("project2InC1", Array("Build C1 Name")) ++
          createModuleWithSourceSet("project3InC1WithSameName", Array("Build C1 Name", "same name in c1")) ++
          createModuleWithSourceSet("project4InC1WithSameName", Array("Build C1 Name", "same name in c1")) ++
          createModuleWithSourceSet("project5InC1WithSameGlobalName", Array("Build C1 Name", "same global name")) ++
          createModuleWithSourceSet("project6InC1WithSameGlobalName", Array("Build C1 Name", "same global name"))

      val modulesFromC2: Seq[module] =
        Seq(rootC2, rootC2Main, rootC2Test) ++
          createModuleWithSourceSet("project1InC2", Array("Build C2 Name")) ++
          createModuleWithSourceSet("project2InC2", Array("Build C2 Name")) ++
          createModuleWithSourceSet("project3InC2WithSameName", Array("Build C2 Name", "same name in c2")) ++
          createModuleWithSourceSet("project4InC2WithSameName", Array("Build C2 Name", "same name in c2")) ++
          createModuleWithSourceSet("project5InC2WithSameGlobalName", Array("Build C2 Name", "same global name")) ++
          createModuleWithSourceSet("project6InC2WithSameGlobalName", Array("Build C2 Name", "same global name"))

      val modulesFromC3: Seq[module] =
        Seq(rootC3, rootC3Main, rootC3Test) ++
          createModuleWithSourceSet("project1InC3", Array("suffix2.root")) ++
          createModuleWithSourceSet("project2InC3", Array("suffix2.root")) ++
          createModuleWithSourceSet("project3InC3WithSameName", Array("suffix2.root", "same name in c3")) ++
          createModuleWithSourceSet("project4InC3WithSameName", Array("suffix2.root", "same name in c3")) ++
          createModuleWithSourceSet("project5InC3WithSameGlobalName", Array("suffix2.root", "same global name")) ++
          createModuleWithSourceSet("project6InC3WithSameGlobalName", Array("suffix2.root", "same global name"))

      val modulesFromC4: Seq[module] =
        Seq(rootC4, rootC4Main, rootC4Test) ++
          createModuleWithSourceSet("project1InC4", Array("suffix1.suffix2.root")) ++
          createModuleWithSourceSet("project2InC4", Array("suffix1.suffix2.root")) ++
          createModuleWithSourceSet("project3InC4WithSameName", Array("suffix1.suffix2.root", "same name in c4")) ++
          createModuleWithSourceSet("project4InC4WithSameName", Array("suffix1.suffix2.root", "same name in c4")) ++
          createModuleWithSourceSet("project5InC4WithSameGlobalName", Array("suffix1.suffix2.root", "same global name")) ++
          createModuleWithSourceSet("project6InC4WithSameGlobalName", Array("suffix1.suffix2.root", "same global name"))


      modules := Seq(root, rootMain, rootTest) ++:
        modulesFromRoot ++:
        modulesFromC1 ++:
        modulesFromC2 ++:
        modulesFromC3 ++:
        modulesFromC4
    }
  )

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_JavaLanguageLevelAndTargetByteCodeLevel extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  //noinspection TypeAnnotation
  // SCL-16204, SCL-17597
  def javaLanguageLevelAndTargetByteCodeLevel(): Unit = {
    //overriding project jdk (configured in base test class)
    val projectSdk9 = IdeaTestUtil.getMockJdk9
    inWriteAction {
      ProjectJdkTable.getInstance.addJdk(projectSdk9)
    }
    getCurrentExternalProjectSettings.jdk = projectSdk9.getName

    //sbt can't be run with mock project JDK, so ensure it has normal SDK (configured in base test class)
    setSbtSettingsCustomSdk(getJdkConfiguredForTestCase)

    val projectName = "java-language-level-and-target-byte-code-level"
    try runTest(
      new project(projectName) {
        // we expect no other options except -source -target --release or --enable-preview in this test
        // these options are specially handled and saved in the dedicated settings, so we don't expect any extra javacOptions
        javacOptions := Nil
        sdk := JdkByName(projectSdk9.getName)

        def moduleX(name: String, source: LanguageLevel, @Nullable target: String): module = new module(name) {
          javaLanguageLevel := source
          javaTargetBytecodeLevel := target
          javacOptions := Nil
          sdk := JdkByName(projectSdk9.getName)
        }

        def moduleXWithMainTestModules(name: String, source: LanguageLevel, @Nullable target: String): Seq[module] = {
          Seq(
            moduleX(name, sdkLanguageLevel, null),
            moduleX(s"$name.main", source, target),
            moduleX(s"$name.test", source, target)
          )
        }

        val sdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_9

        val root = moduleXWithMainTestModules("java-language-level-and-target-byte-code-level", sdkLanguageLevel, null)

        // Module naming: `source_target_release`
        // `x` means option is missing
        val module_x_x_x = moduleXWithMainTestModules(s"$projectName.module_x_x_x", sdkLanguageLevel, null)

        val module_8_8_x   = moduleXWithMainTestModules(s"$projectName.module_8_8_x", LanguageLevel.JDK_1_8, "8")
        val module_8_11_x  = moduleXWithMainTestModules(s"$projectName.module_8_11_x", LanguageLevel.JDK_1_8, "11")
        val module_11_8_x  = moduleXWithMainTestModules(s"$projectName.module_11_8_x", LanguageLevel.JDK_11, "8")
        val module_11_11_x = moduleXWithMainTestModules(s"$projectName.module_11_11_x", LanguageLevel.JDK_11, "11")

        // no explicit target: javac will use source level by default
        val module_8_x_x  = moduleXWithMainTestModules(s"$projectName.module_8_x_x", LanguageLevel.JDK_1_8, null)
        val module_11_x_x = moduleXWithMainTestModules(s"$projectName.module_11_x_x", LanguageLevel.JDK_11, null)
        val module_14_x_x = moduleXWithMainTestModules(s"$projectName.module_14_x_x", LanguageLevel.JDK_14, null)
        val module_15_x_x = moduleXWithMainTestModules(s"$projectName.module_15_x_x", LanguageLevel.JDK_15, null)

        val module_x_8_x  = moduleXWithMainTestModules(s"$projectName.module_x_8_x", sdkLanguageLevel, "8")
        val module_x_11_x = moduleXWithMainTestModules(s"$projectName.module_x_11_x", sdkLanguageLevel, "11")

        val module_x_x_8  = moduleXWithMainTestModules(s"$projectName.module_x_x_8", LanguageLevel.JDK_1_8, "8")
        val module_x_x_11 = moduleXWithMainTestModules(s"$projectName.module_x_x_11", LanguageLevel.JDK_11, "11")

        // Java preview features
        // NOTE: IntelliJ API supports only 2 last preview versions of java language level (in com.intellij.pom.java.LanguageLevel)
        // When a new version of Java releases and IDEA supports it, we should update this test
        //
        // no explicit target: javac will use source level by default
        val module_8_x_x_preview  = moduleXWithMainTestModules(s"$projectName.module_8_x_x_preview", LanguageLevel.JDK_1_8, null) // no preview for Java 8
        val module_11_x_x_preview = moduleXWithMainTestModules(s"$projectName.module_11_x_x_preview", LanguageLevel.JDK_11, null) // no preview for Java 11
        val module_14_x_x_preview = moduleXWithMainTestModules(s"$projectName.module_14_x_x_preview", LanguageLevel.JDK_14, null) // no preview for Java 11
        val module_20_x_x_preview = moduleXWithMainTestModules(s"$projectName.module_20_x_x_preview", LanguageLevel.JDK_20_PREVIEW, null)

        val module_x_x_8_preview  = moduleXWithMainTestModules(s"$projectName.module_x_x_8_preview", LanguageLevel.JDK_1_8, "8")
        val module_x_x_11_preview = moduleXWithMainTestModules(s"$projectName.module_x_x_11_preview", LanguageLevel.JDK_11, "11")
        val module_x_x_14_preview = moduleXWithMainTestModules(s"$projectName.module_x_x_14_preview", LanguageLevel.JDK_14, "14")
        val module_x_x_20_preview = moduleXWithMainTestModules(s"$projectName.module_x_x_20_preview", LanguageLevel.JDK_20_PREVIEW, "20")

        modules :=
          root ++
            module_x_x_x ++
            module_8_8_x ++ module_8_11_x ++ module_11_8_x ++ module_11_11_x ++
            module_8_x_x ++ module_11_x_x ++ module_14_x_x ++ module_15_x_x ++
            module_x_8_x ++ module_x_11_x ++
            module_x_x_8 ++ module_x_x_11 ++
            module_8_x_x_preview ++ module_11_x_x_preview ++ module_14_x_x_preview ++ module_20_x_x_preview ++
            module_x_x_8_preview ++ module_x_x_11_preview ++ module_x_x_14_preview ++ module_x_x_20_preview

      }
    ) finally {
      inWriteAction {
        ProjectJdkTable.getInstance.removeJdk(projectSdk9)
      }
    }
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_MultiBuildProjectWithSpecialCharactersInRootProjectNames extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def multiBuildProjectWithSpecialCharactersInRootProjectNames(): Unit = runTest(
    new project("ro//o/t\\") {
      val buildURI: URI = getTestProjectPath.toCanonicalPath.toUri

      val rootC1: module = new module("Build__1_N_ame") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies ++= Seq(
          new dependency(rootC1Main) {
            isExported := false
          },
          new dependency(rootC1Test) {
            isExported := false
          }
        )
      }
      lazy val rootC1Main: module = new module("Build__1_N_ame.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq()
      }
      lazy val rootC1Test: module = new module("Build__1_N_ame.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq(rootC1Main)
      }
      val root: module = new module("ro__o_t_") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies ++= Seq(
          new dependency(rootMain) {
            isExported := false
          },
          new dependency(rootTest) {
            isExported := false
          }
        )
      }
      lazy val rootMain: module = new module("ro__o_t_.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies ++= Seq(
          new dependency(rootC1Main) { isExported := false },
        )
      }
      lazy val rootTest: module = new module("ro__o_t_.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies ++= Seq(
          new dependency(rootMain) { isExported := false },
          new dependency(rootC1Main) { isExported := false }
        )
      }

      val modulesRoot: Seq[module] =
        Seq(root, rootMain, rootTest) ++
          createModuleWithSourceSet("foo", Array("ro__o_t_"))

      val modulesC1: Seq[module] =
        Seq(rootC1, rootC1Main, rootC1Test) ++
          createModuleWithSourceSet("foo", Array("Build__1_N_ame"))

      modules := modulesRoot ++ modulesC1
    }
  )

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_MultiBuildProjectWithTheSameProjectIdFromIDEAPerspective extends SbtProjectStructureImportingTestCasesUtilities:
  // SBT guarantees us that project ids inside builds are unique. In IDEA in the internal module name all "/" are replaced with "_" and it could happen that in one build
  // the name of one project would be e.g. ro/t and the other one would be ro_t and for SBT project ids uniqueness would be maintained but not for IDEA.
  // In the case of such deduplication, IDEA will add a ~<number> suffix to each sbt source set module (main/test) or sbt nested module (the parent module for main/test).
  // It's done by explicitly setting the ModuleNameDeduplicationStrategy.NUMBER_SUFFIX in these modules.
  @Test
  def multiBuildProjectWithTheSameProjectIdFromIDEAPerspective(): Unit = runTest(
    new project("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
      libraries := scalaLibraries

      val buildURI: URI = getTestProjectPath.toCanonicalPath.toUri

      lazy val c1: module = new module("c1") {
        contentRoots := Seq(getProjectPath + "/c1")
        sbtProjectId := "c1"
        sbtBuildURI := buildURI.resolve("c1/")
        libraryDependencies := Seq()
      }
      lazy val c1Main: module = new module("c1.main") {
        contentRoots := Seq(s"$getProjectPath/c1/src/main", s"$getProjectPath/c1/target/scala-2.13/src_managed/main", s"$getProjectPath/c1/target/scala-2.13/resource_managed/main")
        sbtProjectId := "c1"
        sbtBuildURI := buildURI.resolve("c1/")
        libraryDependencies := scalaLibraries
      }
      lazy val c1Test: module = new module("c1.test") {
        contentRoots := Seq(s"$getProjectPath/c1/src/test", s"$getProjectPath/c1/target/scala-2.13/src_managed/test", s"$getProjectPath/c1/target/scala-2.13/resource_managed/test")
        sbtProjectId := "c1"
        sbtBuildURI := buildURI.resolve("c1/")
        libraryDependencies := scalaLibraries
      }
      val c1Root1: module = new module("ro_t", Array("c1")) {
        libraryDependencies := Seq()
        sbtProjectId := "mod1"
        sbtBuildURI := buildURI.resolve("c1/")
      }
      val c1Root1Main: module = new module("ro_t.main", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "mod1"
        sbtBuildURI := buildURI.resolve("c1/")
      }
      val c1Root1Test: module = new module("ro_t.test", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "mod1"
        sbtBuildURI := buildURI.resolve("c1/")
      }
      val c1Root2: module = new module("ro_t~1", Array("c1")) {
        libraryDependencies := Seq()
        sbtProjectId := "mod2"
        sbtBuildURI := buildURI.resolve("c1/")
      }
      val c1Root2Main: module = new module("ro_t~1.main", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "mod2"
        sbtBuildURI := buildURI.resolve("c1/")
      }

      val c1Root2Test: module = new module("ro_t~1.test", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "mod2"
        sbtBuildURI := buildURI.resolve("c1/")
      }

      lazy val root: module = new module("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective") {
        contentRoots := Seq(getProjectPath)
        sbtProjectId := "multiBuildProjectWithTheSameProjectIdFromIDEAPerspective"
        sbtBuildURI := buildURI
        libraryDependencies := Seq()
      }
      lazy val rootMain: module = new module("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective.main") {
        contentRoots := Seq(s"$getProjectPath/src/main", s"$getProjectPath/target/scala-2.13/src_managed/main", s"$getProjectPath/target/scala-2.13/resource_managed/main")
        sbtProjectId := "multiBuildProjectWithTheSameProjectIdFromIDEAPerspective"
        sbtBuildURI := buildURI
        libraryDependencies := scalaLibraries
        moduleDependencies += new dependency(c1Main) { isExported := false }
      }
      lazy val rootTest: module = new module("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective.test") {
        contentRoots := Seq(s"$getProjectPath/src/test", s"$getProjectPath/target/scala-2.13/src_managed/test", s"$getProjectPath/target/scala-2.13/resource_managed/test")
        sbtProjectId := "multiBuildProjectWithTheSameProjectIdFromIDEAPerspective"
        sbtBuildURI := buildURI
        libraryDependencies := scalaLibraries
        moduleDependencies ++= Seq(
          new dependency(rootMain) { isExported := false },
          new dependency(c1Main) { isExported := false }
        )
      }

      val c1Modules: Seq[module] = Seq(
        c1, c1Main, c1Test,
        c1Root1, c1Root1Main, c1Root1Test,
        c1Root2, c1Root2Main, c1Root2Test,
      )

      modules := Seq(root, rootMain, rootTest) ++ c1Modules
    }
  )

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleTwoBuilds_sbt_1_12_1 extends SbtProjectStructureImportingTestCasesUtilities:
  // Verifies the import process with `-addPluginSbtFile`.
  // It has two builds because the sbt bug (https://github.com/sbt/sbt/issues/8570) fixed in 1.12.1 and 2.0.0-RC9 is related to multi-build setup.
  @Test
  def simpleTwoBuilds_sbt_1_12_1(): Unit = {
    injectVariable(
      getTestProjectPath / "project" / "build.properties",
      "$SBT_VERSION$",
      "1.12.1"
    )

    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    runTest(
      new project("simpleTwoBuilds") {
        libraries := scalaLibraries

        val buildURI: URI = getTestProjectPath.toCanonicalPath.toUri

        modules := Seq(
          new module("simpleTwoBuilds") {
            contentRoots := Seq(getProjectPath)
            sbtProjectId := "simpleTwoBuilds"
            sbtBuildURI := buildURI
            excluded := Seq("target")
          },
          new module("simpleTwoBuilds.main") {
            contentRoots := Seq(
              s"$getProjectPath/src/main",
              s"$getProjectPath/target/scala-2.13/src_managed/main",
              s"$getProjectPath/target/scala-2.13/resource_managed/main"
            )
            sbtProjectId := "simpleTwoBuilds"
            sbtBuildURI := buildURI
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("simpleTwoBuilds.test") {
            contentRoots := Seq(
              s"$getProjectPath/src/test",
              s"$getProjectPath/target/scala-2.13/src_managed/test",
              s"$getProjectPath/target/scala-2.13/resource_managed/test"
            )
            sbtProjectId := "simpleTwoBuilds"
            sbtBuildURI := buildURI
            sources := Nil
            resources := Nil
            testSources := Seq("scala")
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("c2") {
            contentRoots := Seq(s"$getProjectPath/c2")
            sbtProjectId := "c2"
            sbtBuildURI := buildURI.resolve("c2/")
            excluded := Seq("target")
          },
          new module("c2.main") {
            contentRoots := Seq(
              s"$getProjectPath/c2/src/main",
              s"$getProjectPath/c2/target/scala-2.13/src_managed/main",
              s"$getProjectPath/c2/target/scala-2.13/resource_managed/main"
            )
            sbtProjectId := "c2"
            sbtBuildURI := buildURI.resolve("c2/")
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("c2.test") {
            contentRoots := Seq(
              s"$getProjectPath/c2/src/test",
              s"$getProjectPath/c2/target/scala-2.13/src_managed/test",
              s"$getProjectPath/c2/target/scala-2.13/resource_managed/test"
            )
            sbtProjectId := "c2"
            sbtBuildURI := buildURI.resolve("c2/")
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("simpleTwoBuilds.simpleTwoBuilds-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          },
          new module("c2.c2-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          }
        )
      }
    )
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_SimpleTwoBuilds_sbt_2_0_0_RC9 extends SbtProjectStructureImportingTestCasesUtilities:
  // Verifies the import process with `-addPluginSbtFile`
  // It has two builds because the sbt bug (https://github.com/sbt/sbt/issues/8570) fixed in 1.12.1 and 2.0.0-RC9 is related to multi-build setup.
  @Test
  @RequiresJdk(LanguageLevel.JDK_17)
  def simpleTwoBuilds_sbt_2_0_0_RC9(): Unit = {
    injectVariable(
      getTestProjectPath / "project" / "build.properties",
      "$SBT_VERSION$",
      "2.0.0-RC9"
    )

    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    runTest(
      new project("simpleTwoBuilds") {
        libraries := scalaLibraries

        val buildURI: URI = getTestProjectPath.toCanonicalPath.toUri

        modules := Seq(
          new module("simpleTwoBuilds") {
            contentRoots := Seq(getProjectPath)
            sbtProjectId := "simpleTwoBuilds"
            sbtBuildURI := buildURI
            excluded := Seq("target")
          },
          new module("simpleTwoBuilds.main") {
            contentRoots := Seq(
              s"$getProjectPath/src/main",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/simpletwobuilds/src_managed/main",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/simpletwobuilds/resource_managed/main"
            )
            sbtProjectId := "simpleTwoBuilds"
            sbtBuildURI := buildURI
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
            compileOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-2.13.14/simpletwobuilds/classes"
            compileTestOutputPath := null
          },
          new module("simpleTwoBuilds.test") {
            contentRoots := Seq(
              s"$getProjectPath/src/test",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/simpletwobuilds/src_managed/test",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/simpletwobuilds/resource_managed/test"
            )
            sbtProjectId := "simpleTwoBuilds"
            sbtBuildURI := buildURI
            sources := Nil
            resources := Nil
            testSources := Seq("scala")
            testResources := Nil
            libraryDependencies := scalaLibraries
            compileOutputPath := null
            compileTestOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-2.13.14/simpletwobuilds/test-classes"
          },
          new module("c2") {
            contentRoots := Seq(s"$getProjectPath/c2")
            sbtProjectId := "c2"
            sbtBuildURI := buildURI.resolve("c2/")
            excluded := Seq("target")
          },
          new module("c2.main") {
            contentRoots := Seq(
              s"$getProjectPath/c2/src/main",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/c2/src_managed/main",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/c2/resource_managed/main"
            )
            sbtProjectId := "c2"
            sbtBuildURI := buildURI.resolve("c2/")
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
            compileOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-2.13.14/c2/classes"
            compileTestOutputPath := null
          },
          new module("c2.test") {
            contentRoots := Seq(
              s"$getProjectPath/c2/src/test",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/c2/src_managed/test",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/c2/resource_managed/test"
            )
            sbtProjectId := "c2"
            sbtBuildURI := buildURI.resolve("c2/")
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
            compileOutputPath := null
            compileTestOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-2.13.14/c2/test-classes"
          },
          new module("simpleTwoBuilds.simpleTwoBuilds-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          },
          new module("c2.c2-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          }
        )
      }
    )
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_BspDisabledProject extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def bspDisabledProject(): Unit = {
    injectVariable(
      getTestProjectPath / "project" / "build.properties",
      "$SBT_VERSION$",
      "1.12.5"
    )

    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    runTest(
      new project("root") {
        libraries := scalaLibraries
        modules := Seq(
          new module("root") {
            contentRoots := Seq(getProjectPath)
            excluded := Seq("target")
          },
          new module("root.main") {
            contentRoots := Seq(
              s"$getProjectPath/src/main",
              s"$getProjectPath/target/scala-2.13/src_managed/main",
              s"$getProjectPath/target/scala-2.13/resource_managed/main"
            )
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("root.test") {
            contentRoots := Seq(
              s"$getProjectPath/src/test",
              s"$getProjectPath/target/scala-2.13/src_managed/test",
              s"$getProjectPath/target/scala-2.13/resource_managed/test"
            )
            sources := Nil
            resources := Nil
            testSources := Seq("scala")
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("root.root-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          },
          new module("root.foo") {
            contentRoots := Seq(s"$getProjectPath/foo")
            excluded := Seq("target")
          },
          new module("root.foo.main") {
            contentRoots := Seq(
              s"$getProjectPath/foo/src/main",
              s"$getProjectPath/foo/target/scala-2.13/src_managed/main",
              s"$getProjectPath/foo/target/scala-2.13/resource_managed/main"
            )
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("root.foo.test") {
            contentRoots := Seq(
              s"$getProjectPath/foo/src/test",
              s"$getProjectPath/foo/target/scala-2.13/src_managed/test",
              s"$getProjectPath/foo/target/scala-2.13/resource_managed/test"
            )
            sources := Nil
            resources := Nil
            testSources := Seq("scala")
            testResources := Nil
            libraryDependencies := scalaLibraries
          }
        )
      }
    )
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_BspDisabledProject_sbt_2_0_0_RC9 extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  @RequiresJdk(LanguageLevel.JDK_17)
  def bspDisabledProject_sbt_2_0_0_RC9(): Unit = {
    injectVariable(
      getTestProjectPath / "project" / "build.properties",
      "$SBT_VERSION$",
      "2.0.0-RC9"
    )

    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    runTest(
      new project("root") {
        libraries := scalaLibraries
        modules := Seq(
          new module("root") {
            contentRoots := Seq(getProjectPath)
            excluded := Seq("target")
          },
          new module("root.main") {
            contentRoots := Seq(
              s"$getProjectPath/src/main",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/root/src_managed/main",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/root/resource_managed/main"
            )
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
            compileOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-2.13.14/root/classes"
            compileTestOutputPath := null
          },
          new module("root.test") {
            contentRoots := Seq(
              s"$getProjectPath/src/test",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/root/src_managed/test",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/root/resource_managed/test"
            )
            sources := Nil
            resources := Nil
            testSources := Seq("scala")
            testResources := Nil
            libraryDependencies := scalaLibraries
            compileOutputPath := null
            compileTestOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-2.13.14/root/test-classes"
          },
          new module("root.root-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          },
          new module("root.foo") {
            contentRoots := Seq(s"$getProjectPath/foo")
            excluded := Seq("target")
          },
          new module("root.foo.main") {
            contentRoots := Seq(
              s"$getProjectPath/foo/src/main",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/foo/src_managed/main",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/foo/resource_managed/main"
            )
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
            compileOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-2.13.14/foo/classes"
            compileTestOutputPath := null
          },
          new module("root.foo.test") {
            contentRoots := Seq(
              s"$getProjectPath/foo/src/test",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/foo/src_managed/test",
              s"$getProjectPath/target/out/jvm/scala-2.13.14/foo/resource_managed/test"
            )
            sources := Nil
            resources := Nil
            testSources := Seq("scala")
            testResources := Nil
            libraryDependencies := scalaLibraries
            compileOutputPath := null
            compileTestOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-2.13.14/foo/test-classes"
          }
        )
      }
    )
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_ScalafixConfigDisabled extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def scalafixConfigDisabled(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    runTest(
      new project("root") {
        libraries := scalaLibraries
        modules := Seq(
          new module("root") {
            contentRoots := Seq(getProjectPath)
            excluded := Seq("target")
          },
          new module("root.main") {
            contentRoots := Seq(
              s"$getProjectPath/src/main",
              s"$getProjectPath/target/scala-2.13/src_managed/main",
              s"$getProjectPath/target/scala-2.13/resource_managed/main"
            )
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("root.test") {
            contentRoots := Seq(
              s"$getProjectPath/src/test",
              s"$getProjectPath/target/scala-2.13/src_managed/test",
              s"$getProjectPath/target/scala-2.13/resource_managed/test"
            )
            sources := Nil
            resources := Nil
            testSources := Seq("scala")
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("root.root-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          },
        )
      }
    )
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_BspDisabledConfigLevel extends SbtProjectStructureImportingTestCasesUtilities:
  @Test
  def bspDisabledConfigLevel(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
    val scalaSdk = expectedScalaSdkLibraryFromCoursier(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14", SbtProjectSystem.Id, useScalaSdkExtraClasspath = true)
    runTest(
      new project("root") {
        libraries := scalaLibraries
        modules := Seq(
          new module("root") {
            contentRoots := Seq(getProjectPath)
            excluded := Seq("target")
          },
          new module("root.main") {
            contentRoots := Seq(
              s"$getProjectPath/src/main",
              s"$getProjectPath/target/scala-2.13/src_managed/main",
              s"$getProjectPath/target/scala-2.13/resource_managed/main"
            )
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("root.test") {
            contentRoots := Seq(
              s"$getProjectPath/src/test",
              s"$getProjectPath/target/scala-2.13/src_managed/test",
              s"$getProjectPath/target/scala-2.13/resource_managed/test"
            )
            sources := Nil
            resources := Nil
            testSources := Seq("scala")
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("root.root-build") {
            sources := Seq("")
            excluded := Seq("project/target", "target")
          },
          new module("root.foo") {
            contentRoots := Seq(s"$getProjectPath/foo")
            excluded := Seq("target")
          },
          new module("root.foo.main") {
            contentRoots := Nil
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies += scalaSdk
          },
          new module("root.foo.test") {
            contentRoots := Nil
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies += scalaSdk
          },
          new module("root.bar") {
            contentRoots := Seq(s"$getProjectPath/bar")
            excluded := Seq("target")
          },
          new module("root.bar.main") {
            contentRoots := Seq(
              s"$getProjectPath/bar/src/main",
              s"$getProjectPath/bar/target/scala-2.13/src_managed/main",
              s"$getProjectPath/bar/target/scala-2.13/resource_managed/main"
            )
            sources := Seq("scala")
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies := scalaLibraries
          },
          new module("root.bar.test") {
            contentRoots := Nil
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            libraryDependencies += scalaSdk
          },
        )
      }
    )
  }

@RunWith(classOf[SbtProjectStructureImportingRunner])
class SbtProjectStructureImportingTestCase_ManagedScalaInstanceOff extends SbtProjectStructureImportingTestCasesUtilities:
  // When managed scalaInstance is disabled (SCL-24321), sbt behaves differently depending on the version:
  // - sbt < 1.12.0: throws "Missing Scala tool configuration", which sbt-structure silently ignores
  //   (see https://github.com/JetBrains/sbt-structure/commit/92d78ea4b4fe7dbb48e586751f957d420136a809)
  // - sbt >= 1.12.0: returns a scalaInstance with version 0.0.0 and no jars, which sbt-structure filters out
  //   (see https://github.com/JetBrains/sbt-structure/commit/ff960b9e7c2ff801652881d4482dab197666e7b9)
  // In both cases the project is still imported.
  // See https://www.scala-sbt.org/1.x/docs/Configuring-Scala.html#Configuring+Scala+tool+dependencies
  @Test
  def managedScalaInstanceOff(): Unit = runTest(
    new project("scalaInstance") {
      val scalaSdk_2_13_14: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")

      lazy val scalaInstance: module = new module("scalaInstance")
      lazy val scalaInstanceMain: module = new module("scalaInstance.main") { libraryDependencies := scalaSdk_2_13_14 }
      lazy val scalaInstanceTest: module = new module("scalaInstance.test") { libraryDependencies := scalaSdk_2_13_14 }

      lazy val project1: module = new module("scalaInstance.project1") { libraryDependencies := Nil }
      lazy val project1Main: module = new module("scalaInstance.project1.main") { libraryDependencies := Nil }
      lazy val project1Test: module = new module("scalaInstance.project1.test") { libraryDependencies := Nil }

      modules := Seq(
        scalaInstance,
        scalaInstanceMain,
        scalaInstanceTest,
        project1,
        project1Main,
        project1Test
      )
    }
  )

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

  protected def customSbtContentRootsForParent(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("src/main/java", JavaSourceRootType.SOURCE),
      ("src/main/scala", JavaSourceRootType.SOURCE),
      (s"src/main/scala-2.$binaryVersion", JavaSourceRootType.SOURCE),
      ("src/test/java", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
      (s"src/test/scala-2.$binaryVersion", JavaSourceRootType.TEST_SOURCE),
      ("src/main/resources", JavaResourceRootType.RESOURCE),
      ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply)

  protected def customSbtContentRootsForMain(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.SOURCE),
      ("scala", JavaSourceRootType.SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.SOURCE),
      ("resources", JavaResourceRootType.RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply)

  protected def customSbtContentRootsForTest(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.TEST_SOURCE),
      ("scala", JavaSourceRootType.TEST_SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.TEST_SOURCE),
      ("resources", JavaResourceRootType.TEST_RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply)
