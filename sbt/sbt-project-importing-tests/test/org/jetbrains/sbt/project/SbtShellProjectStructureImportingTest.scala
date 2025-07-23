package org.jetbrains.sbt.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.SbtVersion
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class SbtShellProjectStructureImportingTest extends SbtProjectStructureImportingLike {

  import ProjectStructureDsl._

  override def setUp(): Unit = {
    getCurrentExternalProjectSettings.useSbtShellForImport = true
    super.setUp()
    inWriteAction(ProjectRootManager.getInstance(getProject).setProjectSdk(getJdkConfiguredForTestCase))

    if (SystemInfo.isLinux) {
      //noinspection ApiStatus,UnstableApiUsage
      ThreadLeakTracker.longRunningThreadCreated(ApplicationManager.getApplication, "SystemPropertyWatcher")
    }
  }

  override protected def setUpFixtures(): Unit = {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(getName, getTestProjectPath, useDirectoryBasedStorageFormat()).getFixture
    myTestFixture.setUp()
  }

  override protected def getProjectPath: String = getTestProjectPath.toCanonicalPath.toString

  override protected def enableSeparateModulesForProdTest: Boolean = true

  // Tests latest sbt 1.x.
  def testSimple(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
    runSimpleTest("simple", "2.13", scalaLibraries)

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    junit.framework.TestCase.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      myProject.modulesWithScala.map(_.getName),
    )

    // TODO: Propagate the structure dump output for sbt-shell, currently it is empty
//    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
//    junit.framework.TestCase.assertTrue(
//      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
//      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
//    )
  }

  // Tests sbt 1.5.5.
  def testSimple_Scala3(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("3.0.2")
    runSimpleTest("simple-scala3", "3.0.2", scalaLibraries, DefaultSbtContentRootsScala3, DefaultMainSbtContentRootsScala3, DefaultTestSbtContentRootsScala3)
  }

  // Tests sbt 1.7.2.
  def testCompileOrder(): Unit = {
    runTest(new project("compile-order-unspecified") {
      modules := Seq(
        new module("compile-order-unspecified"),
        new module("compile-order-unspecified.main") {
          compileOrder := CompileOrder.Mixed
        },
        new module("compile-order-unspecified.test") {
          compileOrder := CompileOrder.Mixed
        },
        new module("compile-order-unspecified.compile-order-mixed"),
        new module("compile-order-unspecified.compile-order-mixed.main") {
          compileOrder := CompileOrder.Mixed
        },
        new module("compile-order-unspecified.compile-order-mixed.test") {
          compileOrder := CompileOrder.Mixed
        },
        new module("compile-order-unspecified.compile-order-scala-then-java"),
        new module("compile-order-unspecified.compile-order-scala-then-java.main") {
          compileOrder := CompileOrder.ScalaThenJava
        },
        new module("compile-order-unspecified.compile-order-scala-then-java.test") {
          compileOrder := CompileOrder.ScalaThenJava
        },
        new module("compile-order-unspecified.compile-order-java-then-scala"),
        new module("compile-order-unspecified.compile-order-java-then-scala.main") {
          compileOrder := CompileOrder.JavaThenScala
        },
        new module("compile-order-unspecified.compile-order-java-then-scala.test") {
          compileOrder := CompileOrder.JavaThenScala
        }
      )
    })
  }

  // Tests latest sbt 1.x.
  def testMultiModule(): Unit = runTest(
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
          new dependency(barMain) { isExported := false },
          new dependency(fooMain) { isExported := false }
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

  // Tests sbt 2.x.
  def testSimpleSbt2Latest(): Unit = {
    val expectedScala_3_3 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("3.3.3")
    val expectedScala_3_6 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("3.6.2")

    val expectedScalaLibraries = expectedScala_3_3 ++ expectedScala_3_6

    injectVariable(
      getTestProjectPath / "project" / "build.properties",
      "$LATEST_SBT_2$",
      SbtVersion.Latest.Sbt_2.minor
    )

    runTest(
      new project("root") {
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
            contentRoots := Seq(
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
            contentRoots := Seq(
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
            contentRoots := Seq(
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
            contentRoots := Seq(
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
            contentRoots := Seq(
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
            contentRoots := Seq(
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
    junit.framework.TestCase.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq(
        "root.main",
        "root.test",
        "root.subProject1.main",
        "root.subProject1.test",
        "root.subProject2.main",
        "root.subProject2.test",
      ).sorted,
      myProject.modulesWithScala.map(_.getName).sorted,
    )

    // TODO: Propagate the structure dump output for sbt-shell, currently it is empty
//    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
//    junit.framework.TestCase.assertTrue(
//      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
//      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
//    )

    assertDirectoryCompletionVariantsForProjectPaths(
      DefaultSbtContentRootsScala3,
      DefaultMainSbtContentRootsScala3,
      DefaultTestSbtContentRootsScala3,
      myProject.baseDir.getPath,
      myProject.baseDir.getPath + "/subProject1",
      myProject.baseDir.getPath + "/subProject2"
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
    ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  private def customSbtContentRootsForMain(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.SOURCE),
      ("scala", JavaSourceRootType.SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.SOURCE),
      ("resources", JavaResourceRootType.RESOURCE),
    ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  private def customSbtContentRootsForTest(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.TEST_SOURCE),
      ("scala", JavaSourceRootType.TEST_SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.TEST_SOURCE),
      ("resources", JavaResourceRootType.TEST_RESOURCE),
    ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  private def simpleSbtIvyBasedTest(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkFromIvy(useEnv = true)("2.12.10")

    runSimpleTest("simple", "2.12", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = customSbtContentRootsForParent(12),
      expectedSbtCompletionVariantsForMainModule = customSbtContentRootsForMain(12),
      expectedSbtCompletionVariantsForTestModule = customSbtContentRootsForTest(12),
      mutedNotificationTitles = Seq("Legacy sbt version 0.13.18 detected")
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    junit.framework.TestCase.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      myProject.modulesWithScala.map(_.getName),
    )

    // TODO: Propagate the structure dump output for sbt-shell, currently it is empty
    //    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    //    junit.framework.TestCase.assertTrue(
    //      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
    //      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    //    )
  }

  // Tests sbt 0.13.18.
  def testSimpleSbt013(): Unit = {
    simpleSbtIvyBasedTest()
  }

  // Tests sbt 1.0.4.
  def testSimpleSbt104(): Unit = {
    simpleSbtIvyBasedTest()
  }

  // Tests sbt 1.1.6.
  def testSimpleSbt116(): Unit = {
    simpleSbtIvyBasedTest()
  }

  // Tests sbt 1.2.8.
  def testSimpleSbt128(): Unit = {
    simpleSbtIvyBasedTest()
  }

  // Tests sbt 1.3.13.
  def testSimpleSbt1313(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")

    runSimpleTest("simple", "2.13", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = customSbtContentRootsForParent(13),
      expectedSbtCompletionVariantsForMainModule = customSbtContentRootsForMain(13),
      expectedSbtCompletionVariantsForTestModule = customSbtContentRootsForTest(13)
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    junit.framework.TestCase.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      myProject.modulesWithScala.map(_.getName),
    )

    // TODO: Propagate the structure dump output for sbt-shell, currently it is empty
    //    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    //    junit.framework.TestCase.assertTrue(
    //      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
    //      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    //    )
  }

  // Tests sbt 1.4.9.
  def testSimpleSbt149(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")

    runSimpleTest("simple", "2.13", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = DefaultSbtContentRootsScala213,
      expectedSbtCompletionVariantsForMainModule = DefaultMainSbtContentRootsScala213,
      expectedSbtCompletionVariantsForTestModule = DefaultTestSbtContentRootsScala213
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    junit.framework.TestCase.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      myProject.modulesWithScala.map(_.getName),
    )

    // TODO: Propagate the structure dump output for sbt-shell, currently it is empty
    //    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    //    junit.framework.TestCase.assertTrue(
    //      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
    //      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    //    )
  }

  private def runSimpleTest(
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
      myProject.baseDir.getPath
    )
  }

  private def assertDirectoryCompletionVariantsForProjectPaths(
    expectedSbtCompletionVariantsForParentModule: Seq[ExpectedDirectoryCompletionVariant],
    expectedSbtCompletionVariantsForMainModule: Seq[ExpectedDirectoryCompletionVariant],
    expectedSbtCompletionVariantsForTestModule: Seq[ExpectedDirectoryCompletionVariant],
    projectPaths: String*
  ): Unit = {
    projectPaths.foreach { projectPath =>
      Seq(
        (projectPath, expectedSbtCompletionVariantsForParentModule),
        (s"$projectPath/src/main", expectedSbtCompletionVariantsForMainModule),
        (s"$projectPath/src/test", expectedSbtCompletionVariantsForTestModule)
      ).foreach { case(path, variants) =>
        assertSbtDirectoryCompletionContributorVariants(findVirtualFile(path), variants)
      }
    }
  }
}
