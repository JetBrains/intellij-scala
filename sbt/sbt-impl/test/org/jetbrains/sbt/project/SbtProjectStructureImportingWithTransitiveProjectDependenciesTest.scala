package org.jetbrains.sbt.project

import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.experimental.categories.Category

import java.net.URI

@Category(Array(classOf[SlowTests]))
final class SbtProjectStructureImportingWithTransitiveProjectDependenciesTest extends SbtProjectStructureImportingLike {

  import ProjectStructureDsl._

  override def setUp(): Unit = {
    super.setUp()
    val settings = SbtSettings.getInstance(myProject)
    settings.setInsertProjectTransitiveDependencies(true)
  }

  def testSharedSourcesWithNestedProjectDependencies(): Unit = runTest(
    new project("sharedSourcesWithNestedProjectDependencies") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
      libraries := scalaLibraries

      lazy val root: module = new module("sharedSourcesWithNestedProjectDependencies") {
        contentRoots := Seq(getProjectPath)
        sources := Seq("src/main/scala")
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModule) {
            isExported := true
            scope := DependencyScope.COMPILE
          },
          new dependency(bar) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
          new dependency(dummy) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
        )
      }

      lazy val sharedSourcesModule: module = new module("sharedSourcesWithNestedProjectDependencies-sources") {
        contentRoots := Seq(getProjectPath + "/shared")
        libraryDependencies := scalaLibraries
        sources := Seq("src/main/scala")
      }

      lazy val foo: module = new module("foo") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModule) {
            isExported := true
          }
        )
      }

      lazy val bar: module = new module("bar") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModule) {
            isExported := true
          }
        )
      }

      lazy val dummy: module = new module("dummy") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModule) {
            isExported := true
          },
          new dependency(bar) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
        )
      }

      modules := Seq(root, foo, bar, dummy, sharedSourcesModule)
    }
  )


  /**
   * SCL-12520: Generate shared sources module when it is only used form a single other module
   */
  def testSCL12520(): Unit = runTest(
    new project("scl12520") {
      val sharedModule: module = new module("p1-sources") {
        contentRoots += getProjectPath + "/p1/shared"
      }

      val jvmModule: module = new module("p1") {
        moduleDependencies += new dependency(sharedModule) {
          isExported := true
        }
        contentRoots += getProjectPath + "/p1/jvm"
      }

      val rootModule: module = new module("scl12520") {}
      val rootBuildModule: module = new module("scl12520-build") {}

      modules := Seq(sharedModule, rootModule, rootBuildModule, jvmModule)
    }
  )

  /**
   * SCL-13600: generate all modules when there is a duplicate project id in the sbt build
   * due to references to different builds, or multiple sbt projects being imported independently from IDEA
   */
  def testSCL13600(): Unit = runTest(
    new project("scl13600") {
      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI
      lazy val root: module = new module("root") {
        sbtBuildURI := buildURI
        sbtProjectId := "root"

        moduleDependencies := Seq(
          new dependency(c1) {
            isExported := false
          },
          new dependency(c2) {
            isExported := false
          },
        )
      }

      lazy val c1: module = new module("c1") {
        sbtBuildURI := buildURI.resolve("c1/")
        sbtProjectId := "c1"
        moduleDependencies := Seq()
      }
      lazy val c1Root: module = new module("c1.root", Array("root")) {
        sbtBuildURI := buildURI.resolve("c1/")
        sbtProjectId := "root"
        moduleDependencies := Seq()
      }

      lazy val c2: module = new module("c2") {
        sbtBuildURI := buildURI.resolve("c2/")
        sbtProjectId := "c2"
        moduleDependencies := Seq()
      }
      lazy val c2Root: module = new module("c2.root", Array("root")) {
        sbtBuildURI := buildURI.resolve("c2/")
        sbtProjectId := "root"
        moduleDependencies := Seq()
      }

      modules := Seq(root, c1, c1Root, c2, c2Root)
    }
  )

  def testSCL14635(): Unit = runTest(
    new project("SCL-14635") {
      private val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      private val sbtIdeaPluginGroup = Array("sbtIdeaPlugin")
      private val sbtIdeaShellGroup = Array("sbt-idea-shell")
      private val sbtIdeSettingsGroup = Array("sbt-ide-settings")

      // NOTE: sbtIdeaPlugin also has inner module named `sbt-idea-plugin` (with dashes), but it's separate, non-root module
      val sbtIdeaPlugin = new module("sbtIdeaPlugin", sbtIdeaPluginGroup) {
        sbtBuildURI := new URI("https://github.com/JetBrains/sbt-idea-plugin.git")
        sbtProjectId := "sbtIdeaPlugin"
      }

      val sbtIdeaShell = new module("sbt-idea-shell", sbtIdeaShellGroup) {
        sbtBuildURI := new URI("https://github.com/JetBrains/sbt-idea-shell.git#master")
        sbtProjectId := "root"
      }

      val sbtIdeSettings = new module("sbt-ide-settings", sbtIdeSettingsGroup) {
        sbtBuildURI := new URI("https://github.com/JetBrains/sbt-ide-settings.git")
        sbtProjectId := "sbt-ide-settings"
      }


      modules := Seq(
        new module("SCL-14635") {
          sbtBuildURI := buildURI
          sbtProjectId := "root"
          moduleDependencies := Seq(
            new dependency(sbtIdeaPlugin) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(sbtIdeaShell) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(sbtIdeSettings) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
          )
        },
        new module("SCL-14635-build"),
        sbtIdeaPlugin,
        new module("sbt-idea-plugin", sbtIdeaPluginGroup),
        new module("sbt-declarative-core", sbtIdeaPluginGroup),
        new module("sbt-declarative-packaging", sbtIdeaPluginGroup),
        new module("sbt-declarative-visualizer", sbtIdeaPluginGroup),
        new module("sbtIdeaPlugin-build", sbtIdeaPluginGroup),
        sbtIdeaShell,
        new module("sbt-idea-shell-build", sbtIdeaShellGroup),
        sbtIdeSettings,
        new module("sbt-ide-settings-build", sbtIdeSettingsGroup)
      )
    }
  )

  def testNonSourceConfigurationsWithNestedProjectDependencies():Unit = runTest(
    new project("nonSourceConfigurationsWithNestedProjectDependencies") {

      lazy val proj0: module = new module("proj0") {
        sbtProjectId := "proj0"
        moduleDependencies := Seq()
      }

      lazy val proj1: module = new module("proj1") {
        sbtProjectId := "proj1"
        moduleDependencies := Seq(
          new dependency(proj0) {
            isExported := false
            scope := DependencyScope.TEST
          }
        )
      }

      lazy val proj2: module = new module("proj2") {
        sbtProjectId := "proj2"
        moduleDependencies := Seq(
          new dependency(proj0) {
            isExported := false
            scope := DependencyScope.PROVIDED
          },
          new dependency(proj1) {
            isExported := false
            scope := DependencyScope.PROVIDED
          }
        )
      }

      lazy val proj3: module = new module("proj3") {
        sbtProjectId := "proj3"
        moduleDependencies := Seq(
          new dependency(proj0) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
          new dependency(proj1) {
            isExported := false
            scope := DependencyScope.COMPILE
          }
        )
      }


      lazy val root: module = new module("nonSourceConfigurationsWithNestedProjectDependencies") {
        sbtProjectId := "root"
        moduleDependencies := Seq(
          new dependency(proj2) {
            isExported := false
            scope := DependencyScope.TEST
          }
        )
      }
      modules := Seq(root, proj0, proj1, proj2, proj3)
    }
  )

  def testCrossPlatformWithNestedProjectDependencies(): Unit = runTest(
    new project("crossPlatformWithNestedProjectDependencies") {

      lazy val module1JS = new module("module1JS", Array("module1"))
      lazy val module1JVM = new module("module1JVM", Array("module1"))
      lazy val module1Sources = new module("module1-sources", Array("module1"))

      lazy val module2JS = new module("module2JS", Array("module2")){
        moduleDependencies := Seq(
          new dependency(module1JS) {
            isExported := false
            scope := DependencyScope.TEST
          },
          new dependency(module1Sources) {
            isExported := true
            scope := DependencyScope.TEST
          },
          new dependency(module2Sources) {
            isExported := true
            scope := DependencyScope.COMPILE
          },
        )
      }
      lazy val module2JVM = new module("module2JVM", Array("module2")) {
        moduleDependencies := Seq(
          new dependency(module1JVM) {
            isExported := false
            scope := DependencyScope.TEST
          },
          new dependency(module1Sources) {
            isExported := true
            scope := DependencyScope.TEST
          },
          new dependency(module2Sources) {
            isExported := true
            scope := DependencyScope.COMPILE
          },
        )
      }
      lazy val module2Sources = new module("module2-sources", Array("module2")) {
        moduleDependencies := Seq(
          new dependency(module1JVM) {
            isExported := false
            scope := DependencyScope.TEST
          }
        )
      }

      lazy val module3 = new module("module3") {
        moduleDependencies := Seq(
          new dependency(module2JVM) {
            isExported := false
            scope := DependencyScope.TEST
          },
          new dependency(module2Sources) {
            isExported := true
            scope := DependencyScope.TEST
          },
          new dependency(module1JVM) {
            isExported := false
            scope := DependencyScope.TEST
          },
          new dependency(module1Sources) {
            isExported := true
            scope := DependencyScope.TEST
          }
        )
      }

      lazy val root = new module("root") {
        sbtProjectId := "root"
        moduleDependencies := Seq(
          new dependency(module2JVM) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
          new dependency(module2Sources) {
            isExported := true
            scope := DependencyScope.COMPILE
          }
        )
      }

      modules := Seq(root, module1JS, module1JVM, module1Sources, module2JS, module2JVM, module2Sources, module3)
    }
  )

  def testCustomConfigurationsWithNestedProjectDependencies(): Unit = runTest(
    new project("customConfigurationsWithNestedProjectDependencies") {

      lazy val root: module = new module("customConfigurationsWithNestedProjectDependencies") {
        sbtProjectId := "root"
        moduleDependencies := Seq()
      }

      lazy val foo: module = new module("foo") {
        sbtProjectId := "foo"
        moduleDependencies := Seq(
          new dependency(root) {
            isExported := false
            scope := DependencyScope.TEST
          }
        )
      }

      lazy val utils: module = new module("utils") {
        sbtProjectId := "utils"
        moduleDependencies := Seq(
          new dependency(foo) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
          new dependency(root) {
            isExported := false
            scope := DependencyScope.TEST
          }
        )
      }
      modules := Seq(utils, foo, root)
    }
  )

}