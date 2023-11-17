package org.jetbrains.sbt.project

import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

import java.net.URI

@Category(Array(classOf[SlowTests]))
final class SbtProjectStructureImportingTest_TransitiveProjectDependenciesEnabled extends SbtProjectStructureImportingLike {

  import ProjectStructureDsl._

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
    new project("root") {
      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      val rootC1: module = new module("Build C1 Name") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq()
      }
      val rootC2: module = new module("Build C2 Name") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c2/")
        moduleDependencies := Seq()
      }
      val rootC3: module = new module("root1") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      val rootC4: module = new module("root2") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      val root: module = new module("root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies := Seq(
          new dependency(rootC1) {
            isExported := false
          },
          new dependency(rootC2) {
            isExported := false
          },
          new dependency(rootC3) {
            isExported := false
          },
          new dependency(rootC4) {
            isExported := false
          },
        )
      }

      val modulesFromRoot: Seq[module] = Seq(
        new module("project1InRootBuild", Array("root")),
        new module("project2InRootBuild", Array("root")),
        new module("project3InRootBuildWithSameName", Array("root", "same name in root build")),
        new module("project4InRootBuildWithSameName", Array("root", "same name in root build")),
        new module("project5InRootBuildWithSameGlobalName", Array("root", "same global name")),
        new module("project6InRootBuildWithSameGlobalName", Array("root", "same global name")),
      )
      val modulesFromC1: Seq[module] = Seq(
        rootC1,
        new module("project1InC1", Array("Build C1 Name")),
        new module("project2InC1", Array("Build C1 Name")),
        new module("project3InC1WithSameName", Array("Build C1 Name", "same name in c1")),
        new module("project4InC1WithSameName", Array("Build C1 Name", "same name in c1")),
        new module("project5InC1WithSameGlobalName", Array("Build C1 Name", "same global name")),
        new module("project6InC1WithSameGlobalName", Array("Build C1 Name", "same global name")),
      )
      val modulesFromC2: Seq[module] = Seq(
        rootC2,
        new module("project1InC2", Array("Build C2 Name")),
        new module("project2InC2", Array("Build C2 Name")),
        new module("project3InC2WithSameName", Array("Build C2 Name", "same name in c2")),
        new module("project4InC2WithSameName", Array("Build C2 Name", "same name in c2")),
        new module("project5InC2WithSameGlobalName", Array("Build C2 Name", "same global name")),
        new module("project6InC2WithSameGlobalName", Array("Build C2 Name", "same global name")),
      )
      val modulesFromC3: Seq[module] = Seq(
        rootC3,
        new module("project1InC3", Array("root1")),
        new module("project2InC3", Array("root1")),
        new module("project3InC3WithSameName", Array("root1", "same name in c3")),
        new module("project4InC3WithSameName", Array("root1", "same name in c3")),
        new module("project5InC3WithSameGlobalName", Array("root1", "same global name")),
        new module("project6InC3WithSameGlobalName", Array("root1", "same global name")),
      )
      val modulesFromC4: Seq[module] = Seq(
        rootC4,
        new module("project1InC4", Array("root2")),
        new module("project2InC4", Array("root2")),
        new module("project3InC4WithSameName", Array("root2", "same name in c4")),
        new module("project4InC4WithSameName", Array("root2", "same name in c4")),
        new module("project5InC4WithSameGlobalName", Array("root2", "same global name")),
        new module("project6InC4WithSameGlobalName", Array("root2", "same global name")),
      )

      modules := root +:
        modulesFromRoot ++:
        modulesFromC1 ++:
        modulesFromC2 ++:
        modulesFromC3 ++:
        modulesFromC4
    }
  )

  def testSCL14635(): Unit = runTest(
    new project("SCL-14635") {
      private val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      private val sbtIdeaPluginGroup = Array("sbtIdeaPlugin")
      private val sbtIdeaShellGroup = Array("sbt-idea-shell")
      private val sbtIdeSettingsGroup = Array("sbt-ide-settings")

      // NOTE: sbtIdeaPlugin also has inner module named `sbt-idea-plugin` (with dashes), but it's separate, non-root module
      val sbtIdeaPlugin = new module("sbtIdeaPlugin") {
        sbtBuildURI := new URI("https://github.com/JetBrains/sbt-idea-plugin.git")
        sbtProjectId := "sbtIdeaPlugin"
      }

      val sbtIdeaShell = new module("sbt-idea-shell") {
        sbtBuildURI := new URI("https://github.com/JetBrains/sbt-idea-shell.git#master")
        sbtProjectId := "root"
      }

      val sbtIdeSettings = new module("sbt-ide-settings") {
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
        new module("SCL-14635-build", Array("SCL-14635")),
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

      lazy val root = new module("crossPlatformWithNestedProjectDependencies") {
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

  def testSharedSourcesInsideMultiBUILDProject(): Unit = runTest(
    new project("sharedSourcesInsideMultiBUILDProject") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
      libraries := scalaLibraries

      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      lazy val c1: module = new module("c1") {
        contentRoots := Seq(getProjectPath + "/c1")
        sbtProjectId := "c1"
        sbtBuildURI := buildURI.resolve("c1/")
        libraryDependencies := scalaLibraries
      }

      lazy val root: module = new module("sharedSourcesInsideMultiBUILDProject") {
        contentRoots := Seq(getProjectPath)
        sbtProjectId := "sharedSourcesInsideMultiBUILDProject"
        sbtBuildURI := buildURI
        libraryDependencies := scalaLibraries
        moduleDependencies += new dependency(c1) { isExported := false }
      }

      val sharedSourcesModuleInC1: module = new module("c1-sources", Array("c1")) {
        libraryDependencies := scalaLibraries
      }
      val c1Modules: Seq[module] = Seq(
        sharedSourcesModuleInC1,
        new module("foo", Array("c1")) {
          libraryDependencies := scalaLibraries
          sbtProjectId := "foo"
          sbtBuildURI := buildURI.resolve("c1/")
          moduleDependencies += new dependency(sharedSourcesModuleInC1) { isExported := true }

        },
        new module("bar", Array("c1")) {
          libraryDependencies := scalaLibraries
          sbtProjectId := "bar"
          sbtBuildURI := buildURI.resolve("c1/")
          moduleDependencies += new dependency(sharedSourcesModuleInC1) { isExported := true }
        }
      )

      modules := c1 +: root +: c1Modules
    }
  )

  // SBT guarantees us that project ids inside BUILDs are unique. In IDEA in the internal module name all "/" are replaced with "_" and it could happen that in one build
  // the name of one project would be e.g. ro/t and the other one would be ro_t and for SBT project ids uniqueness would be maintained but not for IDEA.
  // In such case we should handle it and append number suffix to one of the module name
  def testMultiBUILDProjectWithTheSameProjectIdFromIDEAPerspective(): Unit = runTest(
    new project("multiBUILDProjectWithTheSameProjectIdFromIDEAPerspective") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
      libraries := scalaLibraries

      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      lazy val c1: module = new module("c1") {
        contentRoots := Seq(getProjectPath + "/c1")
        sbtProjectId := "c1"
        sbtBuildURI := buildURI.resolve("c1/")
        libraryDependencies := scalaLibraries
      }

      lazy val root: module = new module("multiBUILDProjectWithTheSameProjectIdFromIDEAPerspective") {
        contentRoots := Seq(getProjectPath)
        sbtProjectId := "multiBUILDProjectWithTheSameProjectIdFromIDEAPerspective"
        sbtBuildURI := buildURI
        libraryDependencies := scalaLibraries
        moduleDependencies += new dependency(c1) { isExported := false }
      }


      val c1Modules: Seq[module] = Seq(
        new module("ro_t", Array("c1")) {
          libraryDependencies := scalaLibraries
          sbtProjectId := "mod1"
          sbtBuildURI := buildURI.resolve("c1/")
        },
        new module("ro_t1", Array("c1")) {
          libraryDependencies := scalaLibraries
          sbtProjectId := "mod2"
          sbtBuildURI := buildURI.resolve("c1/")
        }
      )

      modules := c1 +: root +: c1Modules
    }
  )

}