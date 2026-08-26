package org.jetbrains.sbt.project

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.SbtVersion
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.experimental.categories.Category

/**
 * Full project-structure importing suite for the default separate main/test module layout.
 *
 * Extend this class only when a variant should run the whole suite with different import settings, such as sbt shell import.
 * Tests that only need project-structure importing utilities should extend [[SbtProjectStructureImportingTestBase]] instead.
 *
 * @see [[SbtProjectStructureImportingTest]]
 * @see [[SbtShellProjectStructureImportingTestBase]]
 * @see [[SbtProjectStructureImportingTest_LegacyModulesLayout]]
 * @todo ensure there is a test for SCL-19673 for the BSP external system as well
 */
@Category(Array(classOf[SlowTests]))
abstract class SbtProjectStructureImportingSuiteBase extends SbtProjectStructureImportingTestBase {

  import ProjectStructureDsl.*

  /**
   * Resolves the test data project directory path.
   *
   * This method strips the `_sbt_*` version suffix from the test method name.
   * This allows multiple version-specific tests (e.g., [[testSimpleTwoBuilds_sbt_1_12_1]]) to share the same underlying test data directory.
   */
  override protected def getTestDataProjectPath: String = {
    val testName = getTestName(true).replaceAll("_sbt_.*$", "")
    generateTestProjectPath(testName)
  }

  private def assertErrorOutputHasNotFailedProjectImport(): Unit = {
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
            testResources:= Nil
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

  @RequiresJdk(LanguageLevel.JDK_17)
  def testSimpleSbt2Latest(): Unit = {
    val expectedScala_3_3 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("3.3.3", useScalaSdkExtraClasspath = false)
    val expectedScala_3_6 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("3.6.2", useScalaSdkExtraClasspath = false)

    val expectedScalaLibraries = expectedScala_3_3 ++ expectedScala_3_6

    injectVariable(
      getTestProjectPath / "project" / "build.properties",
      "$SBT_VERSION$",
      SbtVersion.Latest.Sbt_2.minor
    )

    runTest(
      expected = new project("root") {
        libraries := expectedScalaLibraries

        // ATTENTION: since sbt 2.0:
        // 1. there is only one target dir in the build root and it's hardcoded as "target".
        // 2. all compilation output is located in the root target directory
        // See details:
        //   - https://github.com/sbt/sbt/issues/3681 (it's WIP currently, 10 Feb 2025)
        //   - https://github.com/sbt/sbt/issues/8037
        modules := Seq(
          new module("root.root-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
          },
          new module("root") {
            contentRoots := Seq(getProjectPath)
            libraryDependencies := Nil
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            excluded := Seq("target")
            compileOutputPath := null
            compileTestOutputPath := null
          },
          new module("root.main") {
            contentRoots :=  Seq(
              s"$getProjectPath/src/main",
              s"$getProjectPath/target/out/jvm/scala-3.3.3/root/src_managed/main",
              s"$getProjectPath/target/out/jvm/scala-3.3.3/root/resource_managed/main"
            )
            libraryDependencies := expectedScala_3_3
            sources := Seq("scala", "java")
            resources := Seq("resources")
            testSources := Nil
            testResources := Nil
            excluded := Nil
            compileOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-3.3.3/root/classes"
            compileTestOutputPath := null
          },
          new module("root.test") {
            contentRoots :=  Seq(
              s"$getProjectPath/src/test",
              s"$getProjectPath/target/out/jvm/scala-3.3.3/root/src_managed/test",
              s"$getProjectPath/target/out/jvm/scala-3.3.3/root/resource_managed/test"
            )
            libraryDependencies := expectedScala_3_3
            sources := Nil
            resources := Nil
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            excluded := Nil
            compileOutputPath := null
            compileTestOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-3.3.3/root/test-classes"
          },

          new module("root.subProject1") {
            contentRoots := Seq(s"$getProjectPath/subProject1")
            libraryDependencies := Nil
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            excluded := Seq("target")
            compileOutputPath := null
            compileTestOutputPath := null
          },
          new module("root.subProject1.main") {
            contentRoots :=  Seq(
              s"$getProjectPath/subProject1/src/main",
              s"$getProjectPath/target/out/jvm/scala-3.3.3/subproject1/src_managed/main",
              s"$getProjectPath/target/out/jvm/scala-3.3.3/subproject1/resource_managed/main"
            )
            libraryDependencies := expectedScala_3_3
            sources := Seq("scala", "java")
            resources := Seq("resources")
            testSources := Nil
            testResources := Nil
            excluded := Nil
            compileOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-3.3.3/subproject1/classes"
            compileTestOutputPath := null
          },
          new module("root.subProject1.test") {
            contentRoots :=  Seq(
              s"$getProjectPath/subProject1/src/test",
              s"$getProjectPath/target/out/jvm/scala-3.3.3/subproject1/src_managed/test",
              s"$getProjectPath/target/out/jvm/scala-3.3.3/subproject1/resource_managed/test"
            )
            libraryDependencies := expectedScala_3_3
            sources := Nil
            resources := Nil
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            excluded := Nil
            compileOutputPath := null
            compileTestOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-3.3.3/subproject1/test-classes"
          },


          new module("root.subProject2") {
            contentRoots := Seq(s"$getProjectPath/subProject2")
            libraryDependencies := Nil
            sources := Nil
            resources := Nil
            testSources := Nil
            testResources := Nil
            excluded := Seq("target")
            compileOutputPath := null
            compileTestOutputPath := null
          },
          new module("root.subProject2.main") {
            contentRoots :=  Seq(
              s"$getProjectPath/subProject2/src/main",
              s"$getProjectPath/target/out/jvm/scala-3.6.2/subproject2/src_managed/main",
              s"$getProjectPath/target/out/jvm/scala-3.6.2/subproject2/resource_managed/main"
            )
            libraryDependencies := expectedScala_3_6
            sources := Seq("scala", "java")
            resources := Seq("resources")
            testSources := Nil
            testResources := Nil
            excluded := Nil
            compileOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-3.6.2/subproject2/classes"
            compileTestOutputPath := null
          },
          new module("root.subProject2.test") {
            contentRoots :=  Seq(
              s"$getProjectPath/subProject2/src/test",
              s"$getProjectPath/target/out/jvm/scala-3.6.2/subproject2/src_managed/test",
              s"$getProjectPath/target/out/jvm/scala-3.6.2/subproject2/resource_managed/test"
            )
            libraryDependencies := expectedScala_3_6
            sources := Nil
            resources := Nil
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            excluded := Nil
            compileOutputPath := null
            compileTestOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-3.6.2/subproject2/test-classes"
          },
        )
      }
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    Assert.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq(
        "root.main",
        "root.test",
        "root.subProject1.main",
        "root.subProject1.test",
        "root.subProject2.main",
        "root.subProject2.test",
      ).sorted,
      getMyProject.modulesWithScala.map(_.getName).sorted,
    )

    assertErrorOutputHasNotFailedProjectImport()

    assertDirectoryCompletionVariantsForProjectPaths(
      DefaultSbtContentRootsScala3,
      DefaultMainSbtContentRootsScala3,
      DefaultTestSbtContentRootsScala3,
      getMyProject.baseDir.getPath,
      getMyProject.baseDir.getPath + "/subProject1",
      getMyProject.baseDir.getPath + "/subProject2"
    )
  }

  // reduced version of the example project from SCL-23577
  def testProjectIntegrationTestSourcesOutsideContentRoot(): Unit = {
    runTest(
      new project("root") {
        lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
        libraries := scalaLibraries

        lazy val root: module = new module("root") {
          contentRoots := Seq(getProjectPath)
          libraryDependencies := Nil
          moduleDependencies ++= Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false }
          )
        }
        lazy val rootMain: module = new module("root.main") {
          libraryDependencies := scalaLibraries
          moduleDependencies := Nil
          emptySourceResourceDirs(this)
          contentRoots := Seq(
            "%PROJECT_ROOT%/src/main",
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/main"
          )
        }
        lazy val rootTest: module = new module("root.test") {
          libraryDependencies := scalaLibraries
          moduleDependencies += new dependency(rootMain) { isExported := false }
          emptySourceResourceDirs(this)
          contentRoots := Seq(
            "%PROJECT_ROOT%/src/test",
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/test"
          )
        }

        lazy val subProject: module = new module("root.subProject") {
          libraryDependencies := Nil
          moduleDependencies ++= Seq(
            new dependency(subProjectMain) { isExported := false },
            new dependency(subProjectTest) { isExported := false }
          )
          emptySourceResourceDirs(this)
          excluded := Seq("target")
          contentRoots := Seq("%PROJECT_ROOT%/subProject")
        }
        lazy val subProjectMain: module = new module("root.subProject.main") {
          libraryDependencies := scalaLibraries
          moduleDependencies := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/subProject/src/main",
            "%PROJECT_ROOT%/subProject/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/subProject/target/scala-2.13/resource_managed/main"
          )
          sources := Seq("%PROJECT_ROOT%/subProject/src/main/scala")
          resources := Seq("%PROJECT_ROOT%/subProject/src/main/resources")
          testSources := Nil
          testResources := Nil
          excluded := Nil
        }
        lazy val subProjectTest: module = new module("root.subProject.test") {
          libraryDependencies := scalaLibraries
          moduleDependencies += new dependency(subProjectMain) { isExported := false }
          contentRoots := Seq(
            "%PROJECT_ROOT%/subProject/src/test",
            "%PROJECT_ROOT%/subProject/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/subProject/target/scala-2.13/resource_managed/test"
          )
          sources := Nil
          resources := Nil
          testSources := Seq("%PROJECT_ROOT%/subProject/src/test/scala")
          testResources := Seq("%PROJECT_ROOT%/subProject/src/test/resources")
          excluded := Nil
        }

        lazy val subProjectIntegrationTest: module = new module("root.subProject-integration-test") {
          libraryDependencies := Nil
          moduleDependencies ++= Seq(
            new dependency(subProjectIntegrationTestMain) { isExported := false },
            new dependency(subProjectIntegrationTestTest) { isExported := false }
          )
          emptySourceResourceDirs(this)
          contentRoots := Seq("%PROJECT_ROOT%/derived-projects/subProject-integration-test")
          excluded := Seq("target")
        }
        lazy val subProjectIntegrationTestMain: module = new module("root.subProject-integration-test.main") {
          libraryDependencies := scalaLibraries
          moduleDependencies += new dependency(subProjectMain) { isExported := false }
          contentRoots := Seq(
            "%PROJECT_ROOT%/derived-projects/subProject-integration-test/src/main",
            "%PROJECT_ROOT%/derived-projects/subProject-integration-test/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/derived-projects/subProject-integration-test/target/scala-2.13/resource_managed/main"
          )
          sources := Seq("%PROJECT_ROOT%/derived-projects/subProject-integration-test/src/main/scala")
          resources := Seq("%PROJECT_ROOT%/derived-projects/subProject-integration-test/src/main/resources")
          testSources := Nil
          testResources := Nil
          excluded := Nil
        }
        lazy val subProjectIntegrationTestTest: module = new module("root.subProject-integration-test.test") {
          libraryDependencies := scalaLibraries
          moduleDependencies ++= Seq(
            new dependency(subProjectIntegrationTestMain) { isExported := false },
            new dependency(subProjectMain) { isExported := false },
            new dependency(subProjectTest) { isExported := false }
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/subProject/src/it",
            "%PROJECT_ROOT%/derived-projects/subProject-integration-test/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/derived-projects/subProject-integration-test/target/scala-2.13/resource_managed/test",
          )
          sources := Nil
          resources := Nil
          testSources := Seq("%PROJECT_ROOT%/subProject/src/it/scala")
          excluded := Nil
        }

        modules := Seq(
          root,
          rootMain,
          rootTest,
          subProject,
          subProjectMain,
          subProjectTest,
          subProjectIntegrationTest,
          subProjectIntegrationTestMain,
          subProjectIntegrationTestTest,
        )
      }
    )
  }

  // Test cases for scenarios where custom source directories are set in sbt. Covers cases like:
  //  1. The source directory is set to project base directory
  //  2. The same unmanaged source directories exist in different scopes within a single project
  //  3. An unmanaged source directory in one project matches the source directory base in another project
  def testCustomSourceDirectories(): Unit = runTest(
    new project("root") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
      libraries := scalaLibraries

      lazy val root: module = new module("root") {
        contentRoots := Seq()
        libraryDependencies := Nil
        moduleDependencies ++= Seq(
          new dependency(rootMain) { isExported := false },
          new dependency(rootTest) { isExported := false }
        )
      }
      lazy val rootMain: module = new module("root.main") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Nil
        contentRoots := Seq("%PROJECT_ROOT%")
        sources := Seq("%PROJECT_ROOT%/dummy")
        resources := Seq("%PROJECT_ROOT%/resources")
        excluded := Seq("%PROJECT_ROOT%/target")
        emptySourceResourceDirsTest(this)
      }
      lazy val rootTest: module = new module("root.test") {
        libraryDependencies := scalaLibraries
        moduleDependencies += new dependency(rootMain) { isExported := false }
        contentRoots := Seq(
          "%PROJECT_ROOT%/src/test",
          "%PROJECT_ROOT%/foo/src/main",
          "%PROJECT_ROOT%/target/scala-2.13/src_managed/test",
          "%PROJECT_ROOT%/target/scala-2.13/resource_managed/test"
        )
        testSources := Seq("%PROJECT_ROOT%/foo/src/main", "%PROJECT_ROOT%/src/test/scala")
        testResources := Seq("%PROJECT_ROOT%/src/test/resources")
        emptySourceResourceDirsMain(this)
      }

      lazy val foo: module = new module("root.foo") {
        libraryDependencies := Nil
        moduleDependencies ++= Seq(
          new dependency(fooMain) { isExported := false },
          new dependency(fooTest) { isExported := false }
        )
        emptySourceResourceDirs(this)
        excluded := Seq("target")
        contentRoots := Seq("%PROJECT_ROOT%/foo")
      }
      lazy val fooMain: module = new module("root.foo.main") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Nil
        contentRoots := Seq(
          "%PROJECT_ROOT%/foo/src/main/java",
          "%PROJECT_ROOT%/foo/src/main/scala",
          "%PROJECT_ROOT%/foo/src/main/scala-2",
          "%PROJECT_ROOT%/foo/src/main/scala-2.13",
          "%PROJECT_ROOT%/foo/src/main/resources",
          "%PROJECT_ROOT%/foo/target/scala-2.13/src_managed/main",
          "%PROJECT_ROOT%/foo/target/scala-2.13/resource_managed/main"
        )
        sources := Seq("%PROJECT_ROOT%/foo/src/main/scala", "%PROJECT_ROOT%/foo/src/main/java")
        resources := Seq()
        excluded := Nil
        emptySourceResourceDirsTest(this)
      }
      lazy val fooTest: module = new module("root.foo.test") {
        libraryDependencies := scalaLibraries
        moduleDependencies += new dependency(fooMain) { isExported := false }
        contentRoots := Seq(
          "%PROJECT_ROOT%/foo/src/test",
          "%PROJECT_ROOT%/foo/target/scala-2.13/src_managed/test",
          "%PROJECT_ROOT%/foo/target/scala-2.13/resource_managed/test"
        )
        testSources := Seq("%PROJECT_ROOT%/foo/src/test/scala")
        testResources := Nil
        emptySourceResourceDirsMain(this)
      }

      modules := Seq(root, rootMain, rootTest, foo, fooMain, fooTest)
    }
  )

  def testUnmanagedSourceDirIsProjectBase(): Unit =
    runTest(
      new project("root") {
        lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
        libraries := scalaLibraries

        lazy val root: module = new module("root") {
          contentRoots := Seq()
          libraryDependencies := Nil
          moduleDependencies ++= Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false }
          )
        }
        lazy val rootMain: module = new module("root.main") {
          libraryDependencies := scalaLibraries
          moduleDependencies := Nil
          contentRoots := Seq("%PROJECT_ROOT%")
          sources := Seq("%PROJECT_ROOT%/src/main/scala", "%PROJECT_ROOT%")
          resources := Seq("%PROJECT_ROOT%/src/main/resources")
          excluded := Seq("%PROJECT_ROOT%/target")
          emptySourceResourceDirsTest(this)
        }
        lazy val rootTest: module = new module("root.test") {
          libraryDependencies := scalaLibraries
          moduleDependencies += new dependency(rootMain) { isExported := false }
          contentRoots := Seq(
            "%PROJECT_ROOT%/src/test",
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/test"
          )
          testSources := Seq("%PROJECT_ROOT%/src/test/scala")
          testResources := Nil
          emptySourceResourceDirsMain(this)
        }

        modules := Seq(root, rootMain, rootTest)
      }
    )

  def testTheSameSourceBaseDirsInProject(): Unit =
    runTest(
      new project("root") {
        lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
        libraries := scalaLibraries

        lazy val root: module = new module("root") {
          contentRoots := Seq("%PROJECT_ROOT%")
          excluded := Seq("%PROJECT_ROOT%/target")
          libraryDependencies := Nil
          moduleDependencies ++= Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false }
          )
        }
        lazy val rootMain: module = new module("root.main") {
          libraryDependencies := scalaLibraries
          moduleDependencies := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/dummy",
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/main",
          )
          emptySourceResourceDirs(this)
        }
        lazy val rootTest: module = new module("root.test") {
          libraryDependencies := scalaLibraries
          moduleDependencies += new dependency(rootMain) { isExported := false }
          contentRoots := Seq(
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/test"
          )
          emptySourceResourceDirs(this)
        }

        modules := Seq(root, rootMain, rootTest)
      }
    )

  def testContentRootWithEmptyPaths(): Unit = {
    runTest(
      new project("root") {
        val rootMain: module = new module("root.main") {
          moduleDependencies := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/main"
          )
          emptySourceResourceDirs(this)
        }
        val rootTest: module = new module("root.test") {
          moduleDependencies += new dependency(rootMain) { isExported := false }
          emptySourceResourceDirs(this)
          contentRoots := standardRoots("", "test")
        }
        val root: module = new module("root") {
          moduleDependencies := Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%"
          excluded += "target"
        }
        modules := Seq(root, rootMain, rootTest)
      }
    )
  }

  def testOuterSourceDirectory(): Unit = {
    runTest(
      new project("root") {
        val rootMain: module = new module("root.main") {
          moduleDependencies := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/foo/src",
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/main"
          )
          sources := Seq("%PROJECT_ROOT%/foo/src", "%PROJECT_ROOT%/foo/src/main/scala")
        }
        val rootTest: module = new module("root.test") {
          moduleDependencies += new dependency(rootMain) { isExported := false }
          emptySourceResourceDirs(this)
          contentRoots := standardRoots("", "test")
        }
        val root: module = new module("root") {
          moduleDependencies := Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%"
          excluded += "target"
        }
        modules := Seq(root, rootMain, rootTest)
      }
    )
  }

  def testTwoProjectsWithTheSameBases(): Unit = {
    runTest(
      new project("root") {
        val rootMain: module = new module("root.main") {
          moduleDependencies := Nil
          contentRoots := standardRoots("", "main", "3.0.2")
          emptySourceResourceDirs(this)
        }
        val rootTest: module = new module("root.test") {
          moduleDependencies += new dependency(rootMain) { isExported := false }
          emptySourceResourceDirs(this)
          contentRoots := standardRoots("", "test", "3.0.2")
        }
        val root: module = new module("root") {
          moduleDependencies := Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%"
          excluded += "target"
        }

        val dummyMain: module = new module("root.dummy.main") {
          moduleDependencies := Nil
          contentRoots := standardRoots("dummy", "main", "3.0.2")
          emptySourceResourceDirs(this)
        }
        val dummyTest: module = new module("root.dummy.test") {
          moduleDependencies += new dependency(dummyMain) { isExported := false }
          emptySourceResourceDirs(this)
          contentRoots := standardRoots("dummy", "test", "3.0.2")
        }
        val dummy: module = new module("root.dummy") {
          moduleDependencies := Seq(
            new dependency(dummyMain) { isExported := false },
            new dependency(dummyTest) { isExported := false },
          )
          contentRoots := Nil
        }

        val fooMain: module = new module("root.foo.main") {
          moduleDependencies := Nil
          contentRoots := standardRoots("foo", "main", "3.0.2")
          emptySourceResourceDirs(this)
        }
        val fooTest: module = new module("root.foo.test") {
          moduleDependencies += new dependency(fooMain) { isExported := false }
          emptySourceResourceDirs(this)
          contentRoots := standardRoots("foo", "test", "3.0.2")
        }
        val foo: module = new module("root.foo") {
          moduleDependencies := Seq(
            new dependency(fooMain) { isExported := false },
            new dependency(fooTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%/foo"
          excluded += "target"
        }

        modules := Seq(
          root, rootMain, rootTest,
          foo, fooMain, fooTest,
          dummy, dummyMain, dummyTest
        )
      }
    )
  }

  def testTheSameGroupNameWithSlashes(): Unit =
    runTest(
      new project("root") {

        lazy val project1: module = new module("root.dir_mo_d.project1")
        lazy val project1Main: module = new module("root.dir_mo_d.project1.main")
        lazy val project1Test: module = new module("root.dir_mo_d.project1.test")

        lazy val project2: module = new module("root.dir_mo_d.project2")
        lazy val project2Main: module = new module("root.dir_mo_d.project2.main")
        lazy val project2Test: module = new module("root.dir_mo_d.project2.test")

        lazy val root: module = new module("root")
        lazy val rootMain: module = new module("root.main")
        lazy val rootTest: module = new module("root.test")

        modules := Seq(
          root, rootMain, rootTest,
          project1, project1Main, project1Test,
          project2, project2Main, project2Test
        )
      }
    )
}
