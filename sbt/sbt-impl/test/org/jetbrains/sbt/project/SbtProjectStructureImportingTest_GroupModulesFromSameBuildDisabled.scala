package org.jetbrains.sbt.project

import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.project.ProjectExt
import org.junit.experimental.categories.Category

import java.net.URI
import java.nio.file.Path

// This class contains tests from other SbtStructureImportingTest classes, the purpose of which is to test the structure of the modules.
// In other SbtStructureImportingTest classes grouping from the same build is always enabled, so here the tests will be performed when
// the grouping modules from the same build is disabled (the user has disabled it in the settings). Transitive project dependencies are enabled, as this is
// enabled by default.
// TODO remove when groupProjectsFromSameBuild setting will be removed
@Category(Array(classOf[SlowTests]))
final class SbtProjectStructureImportingTest_GroupModulesFromSameBuildDisabled extends SbtProjectStructureImportingLike {

  import ProjectStructureDsl._

  override def setUp(): Unit = {
    super.setUp()
    val projectSettings = getCurrentExternalProjectSettings
    projectSettings.setGroupProjectsFromSameBuild(false)
  }

  def testMultiModule(): Unit = runTest(
    new project("multiModule") {
      lazy val foo: module = new module("foo") {
        moduleDependencies += new dependency(bar) {
          isExported := false
        }
      }

      lazy val bar  = new module("bar")
      lazy val root = new module("multiModule")

      modules := Seq(root, foo, bar)
    })

  def testProjectWithUppercaseName(): Unit = runTest {
    new project("MyProjectWithUppercaseName") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
      libraries ++= scalaLibraries

      modules := Seq(
        new module("MyProjectWithUppercaseName") {
          libraryDependencies ++= scalaLibraries
        },
        new module("MyProjectWithUppercaseName-build") {
        }
      )
    }
  }

  def testSharedSources(): Unit = runTest(
    new project("sharedSourcesProject") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
      libraries := scalaLibraries

      lazy val root: module = new module("sharedSourcesProject") {
        contentRoots := Seq(getProjectPath)
        sources := Seq("src/main/scala")
        libraryDependencies := scalaLibraries
        moduleDependencies := Nil
      }

      lazy val sharedSourcesModule: module = new module("sharedSources-sources") {
        contentRoots := Seq(getProjectPath + "/shared")
        libraryDependencies := scalaLibraries
        sources := Seq("src/main/scala")
      }

      lazy val foo: module = new module("foo") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModule) { isExported := true }
        )
      }

      lazy val bar: module = new module("bar") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModule) { isExported := true }
        )
      }

      modules := Seq(root, foo, bar, sharedSourcesModule)
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
        moduleDependencies += new dependency(sharedModule) { isExported := true }
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
      val rootC3: module = new module("root~1") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      val rootC4: module = new module("root~2") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      val root: module = new module("root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies := Seq(
          new dependency(rootC1) {isExported := false },
          new dependency(rootC2) {isExported := false },
          new dependency(rootC3) {isExported := false },
          new dependency(rootC4) {isExported := false },
        )
      }

      val modulesFromRoot: Seq[module] = Seq(
        new module("project1InRootBuild"),
        new module("project2InRootBuild"),
        new module("project3InRootBuildWithSameName", Array("same name in root build")),
        new module("project4InRootBuildWithSameName", Array("same name in root build")),
        new module("project5InRootBuildWithSameGlobalName", Array("same global name")),
        new module("project6InRootBuildWithSameGlobalName", Array("same global name")),
      )
      val modulesFromC1: Seq[module] = Seq(
        rootC1,
        new module("project1InC1"),
        new module("project2InC1"),
        new module("project3InC1WithSameName", Array("same name in c1")),
        new module("project4InC1WithSameName", Array("same name in c1")),
        new module("project5InC1WithSameGlobalName", Array("same global name")),
        new module("project6InC1WithSameGlobalName", Array("same global name")),
      )
      val modulesFromC2: Seq[module] = Seq(
        rootC2,
        new module("project1InC2"),
        new module("project2InC2"),
        new module("project3InC2WithSameName", Array("same name in c2")),
        new module("project4InC2WithSameName", Array("same name in c2")),
        new module("project5InC2WithSameGlobalName", Array("same global name")),
        new module("project6InC2WithSameGlobalName", Array("same global name")),
      )
      val modulesFromC3: Seq[module] = Seq(
        rootC3,
        new module("project1InC3"),
        new module("project2InC3"),
        new module("project3InC3WithSameName", Array("same name in c3")),
        new module("project4InC3WithSameName", Array("same name in c3")),
        new module("project5InC3WithSameGlobalName", Array("same global name")),
        new module("project6InC3WithSameGlobalName", Array("same global name")),
      )
      val modulesFromC4: Seq[module] = Seq(
        rootC4,
        new module("project1InC4"),
        new module("project2InC4"),
        new module("project3InC4WithSameName", Array("same name in c4")),
        new module("project4InC4WithSameName", Array("same name in c4")),
        new module("project5InC4WithSameGlobalName", Array("same global name")),
        new module("project6InC4WithSameGlobalName", Array("same global name")),
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

      private val sbtBuildModulesGroupName = "sbt-build-modules"

      modules := Seq(
        new module("SCL-14635") {
          sbtBuildURI := buildURI
          sbtProjectId := "root"
        },
        new module("SCL-14635-build", Array(sbtBuildModulesGroupName)),

        // NOTE: sbtIdeaPlugin also has inner module named `sbt-idea-plugin` (with dashes), but it's separate, non-root module
        new module("sbtIdeaPlugin") {
          sbtBuildURI := new URI("https://github.com/JetBrains/sbt-idea-plugin.git")
          sbtProjectId := "sbtIdeaPlugin"
        },
        new module("sbt-idea-plugin"),
        new module("sbt-declarative-core"),
        new module("sbt-declarative-packaging"),
        new module("sbt-declarative-visualizer"),
        new module("sbtIdeaPlugin-build", Array(sbtBuildModulesGroupName)),

        new module("sbt-idea-shell") {
          sbtBuildURI := new URI("https://github.com/JetBrains/sbt-idea-shell.git#master")
          sbtProjectId := "root"
        },
        new module("sbt-idea-shell-build", Array(sbtBuildModulesGroupName)),

        new module("sbt-ide-settings") {
          sbtBuildURI := new URI("https://github.com/JetBrains/sbt-ide-settings.git")
          sbtProjectId := "sbt-ide-settings"
        },
        new module("sbt-ide-settings-build", Array(sbtBuildModulesGroupName))
      )
    }
  )


  def testCrossplatform(): Unit = runTest(
    new project("crossplatform") {
      lazy val root = new module("crossplatform")
      lazy val crossJS = new module("crossJS", Array("cross"))
      lazy val crossJVM = new module("crossJVM", Array("cross"))
      lazy val crossNative = new module("crossNative", Array("cross"))
      lazy val crossSources = new module("cross-sources", Array("cross"))
      lazy val jsJvmSources = new module("js-jvm-sources", Array("cross"))
      lazy val jsNativeSources = new module("js-native-sources", Array("cross"))
      lazy val jvmNativeSources = new module("jvm-native-sources", Array("cross"))

      modules := Seq(root, crossJS, crossJVM, crossNative, crossSources, jsJvmSources, jsNativeSources, jvmNativeSources)
    }
  )

  def testProjectWithModulesWithSameIdsAndNamesWithDifferentCase(): Unit = runTest(
    new project("ProjectWithModulesWithSameIdsAndNamesWithDifferentCase") {
      modules := Seq(
        new module ("ProjectWithModulesWithSameIdsAndNamesWithDifferentCase"),
        new module ("U_MY_MODULE_ID~2", Array("same module name")),
        new module ("U_My_Module_Id~1", Array("same module name")),
        new module ("U_my_module_id", Array("same module name")),
        new module ("X_MY_MODULE_ID~2"),
        new module ("X_My_Module_Id~1"),
        new module ("X_my_module_id"),
        new module ("Y_MY_MODULE_Name~2"),
        new module ("Y_My_Module_Name~1"),
        new module ("Y_my_module_name"),
        new module ("Z_MY_MODULE_Name~2"),
        new module ("Z_My_Module_Name~1"),
        new module ("Z_my_module_name"),
      )
    }
  )

  def testMultiBuildProjectWithSpecialCharactersInRootProjectNames(): Unit = runTest(
    new project("ro//o.t.") {
      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      val rootC1: module = new module("Build__1.N.ame") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq()
      }
      val root: module = new module("ro__o.t.") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies += new dependency(rootC1) { isExported := false }
      }

      val modulesRoot: Seq[module] = Seq(
        root,
        new module("project1"),
      )
      val modulesC1: Seq[module] = Seq(
        rootC1,
        new module("project1~1"),
      )

      modules := modulesRoot ++ modulesC1
    }
  )

  def testCompileOrder(): Unit = {
    runTest(new project("compile-order-unspecified") {
      modules := Seq(
        new module("compile-order-unspecified") {
          compileOrder := CompileOrder.Mixed
        },
        new module("compile-order-mixed") {
          compileOrder := CompileOrder.Mixed
        },
        new module("compile-order-scala-then-java") {
          compileOrder := CompileOrder.ScalaThenJava
        },
        new module("compile-order-java-then-scala") {
          compileOrder := CompileOrder.JavaThenScala
        }
      )
    })
  }

  def testSharedSourcesInsideMultiBuildProject(): Unit = runTest(
      new project("sharedSourcesInsideMultiBuildProject") {
        lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
        libraries := scalaLibraries

        val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

        lazy val c1: module = new module("c1") {
          contentRoots := Seq(getProjectPath + "/c1")
          sbtProjectId := "c1"
          sbtBuildURI := buildURI.resolve("c1/")
          libraryDependencies := scalaLibraries
        }

        lazy val root: module = new module("sharedSourcesInsideMultiBuildProject") {
          contentRoots := Seq(getProjectPath)
          sbtProjectId := "sharedSourcesInsideMultiBuildProject"
          sbtBuildURI := buildURI
          libraryDependencies := scalaLibraries
          moduleDependencies += new dependency(c1) { isExported := false }
        }

        val sharedSourcesModuleInC1: module = new module("c1-sources") {
          libraryDependencies := scalaLibraries
        }
        val c1Modules: Seq[module] = Seq(
          sharedSourcesModuleInC1,
          new module("foo") {
            libraryDependencies := scalaLibraries
            sbtProjectId := "foo"
            sbtBuildURI := buildURI.resolve("c1/")
            moduleDependencies += new dependency(sharedSourcesModuleInC1) { isExported := true }

          },
          new module("bar") {
            libraryDependencies := scalaLibraries
            sbtProjectId := "bar"
            sbtBuildURI := buildURI.resolve("c1/")
            moduleDependencies += new dependency(sharedSourcesModuleInC1) { isExported := true }
          }
        )

        modules := c1 +: root +: c1Modules
      }
    )

  // note: this test is for the case in which an additional project is linked to the project.
  // The linked project is project "simple". The ideProject is generated from "twoLinkedProjects" project
  def testTwoLinkedProjects(): Unit = {
    runTwoLinkedProjectsTest(
      ideProjectName = "testTwoLinkedProjects",
      originalProjectName = "twoLinkedProjects",
      linkedProjectName = "simple",
      ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.5"),
      DefaultSbtContentRootsScala213
    )
  }

  /**
   *
   * @param ideProjectName it is required to pass explicitly ide project name, because if there is more than one linked project, the project is
   *                       not renamed to ProjectData internal name (see [[com.intellij.openapi.externalSystem.service.project.manage.ProjectDataServiceImpl#importData]])
   */
  private def runTwoLinkedProjectsTest(
    ideProjectName: String,
    originalProjectName: String,
    linkedProjectName: String,
    expectedScalaLibraries: Seq[library],
    expectedSbtCompletionVariants: Seq[ExpectedDirectoryCompletionVariant]
  ): Unit = {
    val linkedSbtProjectPath = generateTestProjectPath(linkedProjectName)
    linkSbtProject(
      linkedSbtProjectPath,
      groupProjectsFromSameBuild = false,
      transitiveProjectDependencies = true
    )
    runTest(
      new project(ideProjectName) {
        modules := Seq(
          new module(originalProjectName) {
            contentRoots += getProjectPath
            ProjectStructureDsl.sources := Seq("src/main/scala", "src/main/java")
            testSources := Seq("src/test/scala", "src/test/java")
            resources := Seq("src/main/resources")
            testResources := Seq("src/test/resources")
            excluded := Seq("target")
            libraryDependencies := expectedScalaLibraries
          },
          new module(s"$originalProjectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
          },
          new module(linkedProjectName) {
            contentRoots += linkedSbtProjectPath
            ProjectStructureDsl.sources := Seq("src/main/scala", "src/main/java")
            testSources := Seq("src/test/scala", "src/test/java")
            resources := Seq("src/main/resources")
            testResources := Seq("src/test/resources")
            excluded := Seq("target")
            libraryDependencies := expectedScalaLibraries
          },
          new module(s"$linkedProjectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
          }
        )
      }
    )
    val originalProjectBaseDir = myProject.baseDir
    val vfm = VirtualFileManager.getInstance()
    val linkedProjectBaseDir = vfm.findFileByNioPath(Path.of(linkedSbtProjectPath))
    Seq(linkedProjectBaseDir, originalProjectBaseDir).foreach(assertSbtDirectoryCompletionContributorVariants(_, expectedSbtCompletionVariants))
  }

}
