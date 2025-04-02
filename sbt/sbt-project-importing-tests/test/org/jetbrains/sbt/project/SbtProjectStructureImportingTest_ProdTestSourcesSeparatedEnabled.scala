package org.jetbrains.sbt.project

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{CompilerTester, IdeaTestUtil}
import org.jetbrains.annotations.Nullable
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.data.{CompileOrder, IncrementalityType}
import org.jetbrains.plugins.scala.extensions.{PathExt, RichFile, inWriteAction}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.external.JdkByName
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.jetbrains.sbt.{Sbt, SbtVersion}
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.experimental.categories.Category

import java.net.URI
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.CollectionHasAsScala

// TODO: ensure there is test for SCL-19673 for BSO external system as well
/**
 * @see [[SbtProjectStructureImportingTest]]
 */
@Category(Array(classOf[SlowTests]))
final class SbtProjectStructureImportingTest_ProdTestSourcesSeparatedEnabled extends SbtProjectStructureImportingLike {

  import ProjectStructureDsl._

  override protected def enableSeparateModulesForProdTest = true

  def testSimple(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
    runSimpleTest("simple", "2.13", scalaLibraries)

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    Assert.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      myProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  //noinspection RedundantDefaultArgument
  def testSimple_Scala3(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("3.0.2")
    runSimpleTest("simple-scala3", "3.0.2", scalaLibraries, DefaultSbtContentRootsScala3, DefaultMainSbtContentRootsScala3, DefaultTestSbtContentRootsScala3)
  }

  // note: this test is for the case in which an additional project is linked to the project.
  // The linked project is project "simple". The ideProject is generated from "twoLinkedProjects" project
  def testTwoLinkedProjects(): Unit = {
    val originalProjectName = "twoLinkedProjects"
    val linkedProjectName = "simple"
    val expectedScalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
    val linkedSbtProjectPath = generateTestProjectPath(linkedProjectName)
    linkSbtProject(linkedSbtProjectPath, prodTestSourcesSeparated = true)
    val siProjectPath = FileUtil.toSystemIndependentName(getProjectPath)
    val siLinkedSbtProjectPath = FileUtil.toSystemIndependentName(linkedSbtProjectPath)
    runTest(
      new project("testTwoLinkedProjects") {
        modules := Seq(
          new module(originalProjectName) {
            contentRoots += siProjectPath
            excluded := Seq("target")
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.main") {
            contentRoots := Seq(s"$siProjectPath/src/main", s"$siProjectPath/target/scala-2.13/src_managed/main", s"$siProjectPath/target/scala-2.13/resource_managed/main")
            ProjectStructureDsl.sources := Seq("scala", "java")
            resources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.test") {
            contentRoots := Seq(s"$siProjectPath/src/test", s"$siProjectPath/target/scala-2.13/src_managed/test", s"$siProjectPath/target/scala-2.13/resource_managed/test")
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.$originalProjectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(linkedProjectName) {
            contentRoots += siLinkedSbtProjectPath
            excluded := Seq("target")
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.main") {
            contentRoots := Seq(s"$siLinkedSbtProjectPath/src/main", s"$siLinkedSbtProjectPath/target/scala-2.13/src_managed/main", s"$siLinkedSbtProjectPath/target/scala-2.13/resource_managed/main")
            ProjectStructureDsl.sources := Seq("scala", "java")
            resources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.test") {
            contentRoots := Seq(s"$siLinkedSbtProjectPath/src/test", s"$siLinkedSbtProjectPath/target/scala-2.13/src_managed/test", s"$siLinkedSbtProjectPath/target/scala-2.13/resource_managed/test")
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.$linkedProjectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
            moduleFileDirectoryPath := "simple"
          }
        )
      }
    )
    assertDirectoryCompletionVariantsForProjectPaths(
      DefaultSbtContentRootsScala213,
      DefaultMainSbtContentRootsScala213,
      DefaultTestSbtContentRootsScala213,
      linkedSbtProjectPath,
      getProjectPath
    )
  }

  private def runSimpleTest(
    projectName: String,
    scalaVersion: String,
    expectedScalaLibraries: Seq[library],
    expectedSbtCompletionVariantsForParentModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultSbtContentRootsScala213,
    expectedSbtCompletionVariantsForMainModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultMainSbtContentRootsScala213,
    expectedSbtCompletionVariantsForTestModule: Seq[ExpectedDirectoryCompletionVariant] = DefaultTestSbtContentRootsScala213,
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
      }
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

  /**
   * Test #SCL-23505
   */
  def testMainTestSbtModules(): Unit = {
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

  def testNumberSuffixDeduplicationStrategy():Unit = runTest(
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

  def testUnmanagedDependency(): Unit = runTest(
    new project("unmanagedDependency") {
      val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
      val managedLibrary: library = new library("sbt: org.apache.commons:commons-compress:1.21:jar")
      libraries := scalaLibraries :+ managedLibrary

      lazy val unmanagedLibrary: library = new library(s"sbt: ${Sbt.UnmanagedLibraryName}") {
        libClasses += (getTestProjectDir.toPath / "lib" / "unmanaged.jar").toAbsolutePath.toString
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

  def testSharedSources(): Unit = runTest(
    new project("sharedSourcesProject") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
      libraries := scalaLibraries

      lazy val root: module = new module("sharedSourcesProject") {
        contentRoots := Seq(getProjectPath)
        moduleDependencies ++= Seq(
          new dependency(rootMain) {
            isExported := false
          },
          new dependency(rootTest) {
            isExported := false
          }
        )
      }
      lazy val rootMain: module = new module("sharedSourcesProject.main") {
        contentRoots := Seq(s"$getProjectPath/src/main", s"$getProjectPath/target/scala-2.13/src_managed/main", s"$getProjectPath/target/scala-2.13/resource_managed/main")
        sources := Seq("scala")
        libraryDependencies := scalaLibraries
        moduleDependencies := Nil
      }
      lazy val rootTest: module = new module("sharedSourcesProject.test") {
        contentRoots := Seq(s"$getProjectPath/src/test", s"$getProjectPath/target/scala-2.13/src_managed/test", s"$getProjectPath/target/scala-2.13/resource_managed/test")
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(rootMain)
      }

      lazy val sharedSourcesModule: module = new module("sharedSourcesProject.sharedSources-sources") {
        contentRoots := Seq(getProjectPath + "/shared")
        moduleDependencies ++= Seq(
          new dependency(sharedSourcesModuleMain) {
            isExported := false
          }
        )
      }
      lazy val sharedSourcesModuleMain: module = new module("sharedSourcesProject.sharedSources-sources.main") {
        contentRoots := Seq(s"$getProjectPath/shared/src/main")
        libraryDependencies := scalaLibraries
      }

      lazy val foo: module = new module("sharedSourcesProject.foo") {
        moduleDependencies ++= Seq(
          new dependency(fooMain) {
            isExported := false
          },
          new dependency(fooTest) {
            isExported := false
          }
        )
      }
      lazy val fooMain: module = new module("sharedSourcesProject.foo.main") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModuleMain) { isExported := true }
        )
      }
      lazy val fooTest: module = new module("sharedSourcesProject.foo.test") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModuleMain) { isExported := true },
          new dependency(fooMain) { isExported := false }
        )
      }

      lazy val bar: module = new module("sharedSourcesProject.bar") {
        moduleDependencies ++= Seq(
          new dependency(barMain) {
            isExported := false
          },
          new dependency(barTest) {
            isExported := false
          }
        )
      }
      lazy val barMain: module = new module("sharedSourcesProject.bar.main") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModuleMain) { isExported := true }
        )
      }
      lazy val barTest: module = new module("sharedSourcesProject.bar.test") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModuleMain) { isExported := true },
          new dependency(barMain) { isExported := false }
        )
      }

      modules := Seq(
        root, rootMain, rootTest,
        foo, fooMain, fooTest,
        bar, barMain, barTest,
        sharedSourcesModule, sharedSourcesModuleMain
      )
    }
  )

  def testSbtIdeSettingsRespectIdeExcludedDirectoriesSetting(): Unit = runTest(
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

  /** SCL-12520: Generate a shared sources module even when it is used only from a single target module */
  def testCrossProjectJvmOnly_LegacyCrossBuildPlugin(): Unit = {
    runTest(
      new project("root") {
        val sharedModule: module = new module("root.p1-sources") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/shared"
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin/p1/jvm"
        }
        val sharedModuleMain: module = new module("root.p1-sources.main") {
          sources := Seq(
            "%PROJECT_ROOT%/p1/shared/src/main/scala",
            "%PROJECT_ROOT%/p1/shared/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/shared/src/main"
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin/p1/jvm"
        }
        val sharedModuleTest: module = new module("root.p1-sources.test") {
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/shared/src/test/scala",
            "%PROJECT_ROOT%/p1/shared/src/test/scala-2.13",
          )
          contentRoots += "%PROJECT_ROOT%/p1/shared/src/test"
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin/p1/jvm"
        }

        val jvmModule: module = new module("root.p1") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/jvm"
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin/p1/jvm"
        }
        val jvmModuleMain: module = new module("root.p1.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/main/scala",
            "%PROJECT_ROOT%/p1/jvm/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/main",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin/p1/jvm"
        }
        val jvmModuleTest: module = new module("root.p1.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jvmModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/test/scala",
            "%PROJECT_ROOT%/p1/jvm/src/test/scala-2.13",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/test",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/resource_managed/test",
          )
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin/p1/jvm"
        }

        val rootModule: module = new module("root") {
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin"
        }
        val rootModuleMain: module = new module("root.main") {
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin"
        }
        val rootModuleTest: module = new module("root.test") {
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin"
        }
        val rootBuildModule: module = new module("root.root-build") {
          moduleFileDirectoryPath := "crossProjectJvmOnly_LegacyCrossBuildPlugin"
        }

        modules := Seq(
          sharedModule, sharedModuleMain, sharedModuleTest,
          rootModule, rootModuleMain, rootModuleTest, rootBuildModule,
          jvmModule, jvmModuleMain, jvmModuleTest
        )
      }
    )

    buildCrossProjectAndAssertNoWarningsOrErrors()
    assertNoTargetDirGeneratedInSharedDirectory("%PROJECT_ROOT%/p1/shared")
  }

  /** SCL-12520: Generate a shared sources module even when it is used only from a single target module */
  def testCrossProjectJvmOnly_CrossTypeFull(): Unit = {
    runTest(
      new project("root") {
        val sharedModule: module = new module("root.p1-sources") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/shared"
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull/p1/jvm"
        }
        val sharedModuleMain: module = new module("root.p1-sources.main") {
          sources := Seq(
            "%PROJECT_ROOT%/p1/shared/src/main/scala",
            "%PROJECT_ROOT%/p1/shared/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/shared/src/main"
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull/p1/jvm"
        }
        val sharedModuleTest: module = new module("root.p1-sources.test") {
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/shared/src/test/scala",
            "%PROJECT_ROOT%/p1/shared/src/test/scala-2.13",
          )
          contentRoots += "%PROJECT_ROOT%/p1/shared/src/test"
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull/p1/jvm"
        }

        val jvmModule: module = new module("root.p1") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/jvm"
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull/p1/jvm"
        }
        val jvmModuleMain: module = new module("root.p1.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/main/scala",
            "%PROJECT_ROOT%/p1/jvm/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/main",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull/p1/jvm"
        }
        val jvmModuleTest: module = new module("root.p1.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jvmModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/test/scala",
            "%PROJECT_ROOT%/p1/jvm/src/test/scala-2.13",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/test",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/resource_managed/test",
          )
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull/p1/jvm"
        }

        val rootModule: module = new module("root") {
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull"
        }
        val rootModuleMain: module = new module("root.main") {
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull"
        }
        val rootModuleTest: module = new module("root.test") {
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull"
        }
        val rootBuildModule: module = new module("root.root-build") {
          moduleFileDirectoryPath := "crossProjectJvmOnly_CrossTypeFull"
        }

        modules := Seq(
          sharedModule, sharedModuleMain, sharedModuleTest,
          rootModule, rootModuleMain, rootModuleTest, rootBuildModule,
          jvmModule, jvmModuleMain, jvmModuleTest
        )
      }
    )

    buildCrossProjectAndAssertNoWarningsOrErrors()
    assertNoTargetDirGeneratedInSharedDirectory("%PROJECT_ROOT%/p1/shared")
  }

  def testCrossProjectJvmAndJs_CrossTypeFull(): Unit = {
    runTest(
      new project("root") {
        val sharedModule: module = new module("root.p1.p1-sources") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/shared"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/jvm"
        }
        val sharedModuleMain: module = new module("root.p1.p1-sources.main") {
          sources := Seq(
            "%PROJECT_ROOT%/p1/shared/src/main/scala",
            "%PROJECT_ROOT%/p1/shared/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/shared/src/main"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/jvm"
        }
        val sharedModuleTest: module = new module("root.p1.p1-sources.test") {
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/shared/src/test/scala",
            "%PROJECT_ROOT%/p1/shared/src/test/scala-2.13",
          )
          contentRoots += "%PROJECT_ROOT%/p1/shared/src/test"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/jvm"
        }

        val jvmModule: module = new module("root.p1.p1JVM") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/jvm"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/jvm"
        }
        val jvmModuleMain: module = new module("root.p1.p1JVM.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/main/scala",
            "%PROJECT_ROOT%/p1/jvm/src/main/scala-2.13",
            "%PROJECT_ROOT%/p1_jvm_src_external",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/main",
            "%PROJECT_ROOT%/p1_jvm_src_external",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/jvm"
        }
        val jvmModuleTest: module = new module("root.p1.p1JVM.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jvmModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/test/scala",
            "%PROJECT_ROOT%/p1/jvm/src/test/scala-2.13",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/jvm/src/test",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/jvm/target/scala-2.13/resource_managed/test"
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/jvm"
        }

        val jsModule: module = new module("root.p1.p1JS") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/js"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/js"
        }
        val jsModuleMain: module = new module("root.p1.p1JS.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/js/src/main/scala",
            "%PROJECT_ROOT%/p1/js/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/js/src/main",
            "%PROJECT_ROOT%/p1/js/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/js/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/js"
        }
        val jsModuleTest: module = new module("root.p1.p1JS.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jsModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/js/src/test/scala",
            "%PROJECT_ROOT%/p1/js/src/test/scala-2.13",
            "%PROJECT_ROOT%/p1_js_src_external",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/js/src/test",
            "%PROJECT_ROOT%/p1_js_src_external",
            "%PROJECT_ROOT%/p1/js/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/js/target/scala-2.13/resource_managed/test",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull/p1/js"
        }

        val rootModule: module = new module("root") {
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull"
        }
        val rootModuleMain: module = new module("root.main") {
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull"
        }
        val rootModuleTest: module = new module("root.test") {
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull"
        }
        val rootBuildModule: module = new module("root.root-build") {
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypeFull"
        }

        modules := Seq(
          sharedModule, sharedModuleMain, sharedModuleTest,
          rootModule, rootModuleMain, rootModuleTest, rootBuildModule,
          jvmModule, jvmModuleMain, jvmModuleTest,
          jsModule, jsModuleMain, jsModuleTest,
        )
      }
    )

    buildCrossProjectAndAssertNoWarningsOrErrors()
    assertNoTargetDirGeneratedInSharedDirectory("%PROJECT_ROOT%/p1/shared")
  }

  def testCrossProjectJvmAndJs_CrossTypePure(): Unit = {
    runTest(
      new project("root") {
        val sharedModule: module = new module("root.p1.p1-sources") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.jvm"
        }
        val sharedModuleMain: module = new module("root.p1.p1-sources.main") {
          sources := Seq(
            "%PROJECT_ROOT%/p1/src/main/scala",
            "%PROJECT_ROOT%/p1/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/src/main"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.jvm"
        }
        val sharedModuleTest: module = new module("root.p1.p1-sources.test") {
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/src/test/scala",
            "%PROJECT_ROOT%/p1/src/test/scala-2.13",
          )
          contentRoots += "%PROJECT_ROOT%/p1/src/test"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.jvm"
        }

        val jvmModule: module = new module("root.p1.p1JVM") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/.jvm"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.jvm"
        }
        val jvmModuleMain: module = new module("root.p1.p1JVM.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/.jvm/src/main/scala",
            "%PROJECT_ROOT%/p1/.jvm/src/main/scala-2.13",
            "%PROJECT_ROOT%/p1_jvm_src_external",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.jvm/src/main",
            "%PROJECT_ROOT%/p1_jvm_src_external",
            "%PROJECT_ROOT%/p1/.jvm/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/.jvm/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.jvm"
        }
        val jvmModuleTest: module = new module("root.p1.p1JVM.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jvmModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/.jvm/src/test/scala",
            "%PROJECT_ROOT%/p1/.jvm/src/test/scala-2.13",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.jvm/src/test",
            "%PROJECT_ROOT%/p1/.jvm/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/.jvm/target/scala-2.13/resource_managed/test"
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.jvm"
        }

        val jsModule: module = new module("root.p1.p1JS") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/.js"
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.js"
        }
        val jsModuleMain: module = new module("root.p1.p1JS.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/.js/src/main/scala",
            "%PROJECT_ROOT%/p1/.js/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.js/src/main",
            "%PROJECT_ROOT%/p1/.js/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/.js/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.js"
        }
        val jsModuleTest: module = new module("root.p1.p1JS.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jsModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/.js/src/test/scala",
            "%PROJECT_ROOT%/p1/.js/src/test/scala-2.13",
            "%PROJECT_ROOT%/p1_js_src_external",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.js/src/test",
            "%PROJECT_ROOT%/p1_js_src_external",
            "%PROJECT_ROOT%/p1/.js/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/.js/target/scala-2.13/resource_managed/test",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure/p1/.js"
        }

        val rootModule: module = new module("root") {
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure"
        }
        val rootModuleMain: module = new module("root.main") {
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure"
        }
        val rootModuleTest: module = new module("root.test") {
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure"
        }
        val rootBuildModule: module = new module("root.root-build") {
          moduleFileDirectoryPath := "crossProjectJvmAndJs_CrossTypePure"
        }

        modules := Seq(
          sharedModule, sharedModuleMain, sharedModuleTest,
          rootModule, rootModuleMain, rootModuleTest, rootBuildModule,
          jvmModule, jvmModuleMain, jvmModuleTest,
          jsModule, jsModuleMain, jsModuleTest,
        )
      }
    )

    buildCrossProjectAndAssertNoWarningsOrErrors()
    assertNoTargetDirGeneratedInSharedDirectory("%PROJECT_ROOT%/p1")
  }

  def testCrossProjectJvmAndJsAndNative_CrossTypePure(): Unit = {
    runTest(
      new project("root") {
        val sharedModule: module = new module("root.p1.p1-sources") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1"
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.jvm"
        }
        val sharedModuleMain: module = new module("root.p1.p1-sources.main") {
          sources := Seq(
            "%PROJECT_ROOT%/p1/src/main/scala",
            "%PROJECT_ROOT%/p1/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/src/main"
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.jvm"
        }
        val sharedModuleTest: module = new module("root.p1.p1-sources.test") {
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/src/test/scala",
            "%PROJECT_ROOT%/p1/src/test/scala-2.13",
          )
          contentRoots += "%PROJECT_ROOT%/p1/src/test"
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.jvm"
        }

        val jvmModule: module = new module("root.p1.p1JVM") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/.jvm"
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.jvm"
        }
        val jvmModuleMain: module = new module("root.p1.p1JVM.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/.jvm/src/main/scala",
            "%PROJECT_ROOT%/p1/.jvm/src/main/scala-2.13",
            "%PROJECT_ROOT%/p1_jvm_src_external",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.jvm/src/main",
            "%PROJECT_ROOT%/p1_jvm_src_external",
            "%PROJECT_ROOT%/p1/.jvm/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/.jvm/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.jvm"
        }
        val jvmModuleTest: module = new module("root.p1.p1JVM.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jvmModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/.jvm/src/test/scala",
            "%PROJECT_ROOT%/p1/.jvm/src/test/scala-2.13",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.jvm/src/test",
            "%PROJECT_ROOT%/p1/.jvm/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/.jvm/target/scala-2.13/resource_managed/test"
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.jvm"
        }

        val jsModule: module = new module("root.p1.p1JS") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/.js"
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.js"
        }
        val jsModuleMain: module = new module("root.p1.p1JS.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/.js/src/main/scala",
            "%PROJECT_ROOT%/p1/.js/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.js/src/main",
            "%PROJECT_ROOT%/p1/.js/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/.js/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.js"
        }
        val jsModuleTest: module = new module("root.p1.p1JS.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jsModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/.js/src/test/scala",
            "%PROJECT_ROOT%/p1/.js/src/test/scala-2.13",
            "%PROJECT_ROOT%/p1_js_src_external",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.js/src/test",
            "%PROJECT_ROOT%/p1_js_src_external",
            "%PROJECT_ROOT%/p1/.js/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/.js/target/scala-2.13/resource_managed/test",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.js"
        }

        val nativeModule: module = new module("root.p1.p1Native") {
          sources := Nil
          testSources := Nil
          contentRoots += "%PROJECT_ROOT%/p1/.native"
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.native"
        }
        val nativeModuleMain: module = new module("root.p1.p1Native.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          sources := Seq(
            "%PROJECT_ROOT%/p1/.native/src/main/scala",
            "%PROJECT_ROOT%/p1/.native/src/main/scala-2.13",
          )
          testSources := Nil
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.native/src/main",
            "%PROJECT_ROOT%/p1/.native/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/p1/.native/target/scala-2.13/resource_managed/main",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.native"
        }
        val nativeModuleTest: module = new module("root.p1.p1Native.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(nativeModuleMain) { isExported := false }
          )
          sources := Nil
          testSources := Seq(
            "%PROJECT_ROOT%/p1/.native/src/test/scala",
            "%PROJECT_ROOT%/p1/.native/src/test/scala-2.13",
            "%PROJECT_ROOT%/p1_native_src_external",
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/p1/.native/src/test",
            "%PROJECT_ROOT%/p1_native_src_external",
            "%PROJECT_ROOT%/p1/.native/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/p1/.native/target/scala-2.13/resource_managed/test",
          )
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure/p1/.native"
        }

        val rootModule: module = new module("root") {
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure"
        }
        val rootModuleMain: module = new module("root.main") {
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure"
        }
        val rootModuleTest: module = new module("root.test") {
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure"
        }
        val rootBuildModule: module = new module("root.root-build") {
          moduleFileDirectoryPath := "crossProjectJvmAndJsAndNative_CrossTypePure"
        }

        modules := Seq(
          sharedModule, sharedModuleMain, sharedModuleTest,
          rootModule, rootModuleMain, rootModuleTest, rootBuildModule,
          jvmModule, jvmModuleMain, jvmModuleTest,
          jsModule, jsModuleMain, jsModuleTest,
          nativeModule, nativeModuleMain, nativeModuleTest,
        )
      }
    )

    buildCrossProjectAndAssertNoWarningsOrErrors()
    assertNoTargetDirGeneratedInSharedDirectory("%PROJECT_ROOT%/p1")
  }

  private def assertNoTargetDirGeneratedInSharedDirectory(sharedSourcesRoot: String): Unit = {
    ApplicationManager.getApplication.invokeAndWait(() => {
      inWriteAction {
        myProject.save()
      }
    })

    val sharedSourcesRootDir = Path.of(defaultCompareContext.macroSubstitutor.replaceMacroWithValue(sharedSourcesRoot))
    assertTrue(
      s"Non existing shared source directory passed: $sharedSourcesRootDir",
      Files.exists(sharedSourcesRootDir)
    )

    val targetDir = sharedSourcesRootDir.resolve("target")
    assertFalse(
      s"Unexpected target directory is generated in the shared module: $targetDir",
      Files.exists(targetDir)
    )
  }

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

  def testSCL14635(): Unit = runTest(
    new project("SCL-14635") {
      private val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

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

  def testNonSourceConfigurationsWithNestedProjectDependencies():Unit = {
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
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Main) {
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
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.PROVIDED
            },
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.PROVIDED
            },
            new dependency(proj1Test) {
              isExported := false
              scope := DependencyScope.PROVIDED
            }
          )
        }

        lazy val proj2Test: module = new module(s"$projectName.proj2.test") {
          sbtProjectId := "proj2"
          moduleDependencies := Seq(
            new dependency(proj2Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Test) {
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
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Test) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }

        lazy val proj3Test: module = new module(s"$projectName.proj3.test") {
          sbtProjectId := "proj3"
          moduleDependencies := Seq(
            new dependency(proj3Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj0Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(proj1Test) {
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
            new dependency(proj2Main) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(rootMain) {
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

  def testCrossplatform(): Unit = runTest(
    new project("crossplatform") {

      lazy val rootModules: Seq[module] = createModuleWithSourceSet("crossplatform")
      lazy val crossJSModules: Seq[module] = createModuleWithSourceSet("crossJS", Array("crossplatform", "cross"))
      lazy val crossJVMModules: Seq[module] = createModuleWithSourceSet("crossJVM", Array("crossplatform", "cross"))
      lazy val crossNativeModules: Seq[module] = createModuleWithSourceSet("crossNative", Array("crossplatform", "cross"))
      lazy val crossSourcesModules: Seq[module] = createModuleWithSourceSet("cross-sources", Array("crossplatform", "cross"))
      lazy val jsJvmSourcesModules: Seq[module] = createModuleWithSourceSet("js-jvm-sources", Array("crossplatform", "cross"))
      lazy val jsNativeSourcesModules: Seq[module] = createModuleWithSourceSet("js-native-sources", Array("crossplatform", "cross"))
      lazy val jvmNativeSourcesModules: Seq[module] = createModuleWithSourceSet("jvm-native-sources", Array("crossplatform", "cross"))

      modules :=
        rootModules ++
          crossJSModules ++
          crossJVMModules ++
          crossNativeModules ++
          crossSourcesModules ++
          jsJvmSourcesModules ++
          jsNativeSourcesModules ++
          jvmNativeSourcesModules
    }
  )

  def testCrossPlatformWithNestedProjectDependencies(): Unit = {
    val projectName = "crossPlatformWithNestedProjectDependencies"
    runTest(
      new project(projectName) {

        lazy val module1Sources: module = new module("module1-sources", Array(projectName, "module1")){
          moduleDependencies ++= Seq(
            new dependency(module1SourcesMain) {
              isExported := false
            },
            new dependency(module1SourcesTest) {
              isExported := false
            }
          )
        }
        lazy val module1SourcesMain: module = new module("module1-sources.main", Array(projectName, "module1")){
          moduleDependencies := Seq()
        }
        lazy val module1SourcesTest: module = new module("module1-sources.test", Array(projectName, "module1")){
          moduleDependencies := Seq(
            new dependency(module1JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
          )
        }

        lazy val module1JS: module = new module("module1JS", Array(projectName, "module1")) {
          moduleDependencies ++= Seq(
            new dependency(module1JSMain) {
              isExported := false
            },
            new dependency(module1JSTest) {
              isExported := false
            }
          )
        }
        lazy val module1JSMain: module = new module("module1JS.main", Array(projectName, "module1")) {
          moduleDependencies := Seq(
            new dependency(module1SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            }
          )
        }
        lazy val module1JSTest: module = new module("module1JS.test", Array(projectName, "module1")) {
          moduleDependencies := Seq(
            new dependency(module1JSMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module1SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module1SourcesTest) {
              isExported := true
              scope := DependencyScope.COMPILE
            }
          )
        }
        lazy val module1JVM: module = new module("module1JVM", Array(projectName, "module1")) {
          moduleDependencies ++= Seq(
            new dependency(module1JVMMain) {
              isExported := false
            },
            new dependency(module1JVMTest) {
              isExported := false
            }
          )
        }
        lazy val module1JVMMain: module = new module("module1JVM.main", Array(projectName, "module1")) {
          moduleDependencies := Seq(
            new dependency(module1SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            }
          )
        }
        lazy val module1JVMTest: module = new module("module1JVM.test", Array(projectName, "module1")) {
          moduleDependencies := Seq(
            new dependency(module1JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module1SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module1SourcesTest) {
              isExported := true
              scope := DependencyScope.COMPILE
            }
          )
        }

        lazy val module2JS: module = new module("module2JS", Array(projectName, "module2")) {
          moduleDependencies ++= Seq(
            new dependency(module2JSMain) {
              isExported := false
            },
            new dependency(module2JSTest) {
              isExported := false
            }
          )
        }
        lazy val module2JSMain: module = new module("module2JS.main", Array(projectName, "module2")) {
          moduleDependencies := Seq(
            new dependency(module2SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            }
          )
        }
        lazy val module2JSTest: module = new module("module2JS.test", Array(projectName, "module2")) {
          moduleDependencies := Seq(
            new dependency(module1JSMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module1SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2SourcesTest) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2JSMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }
        lazy val module2JVM: module = new module("module2JVM", Array(projectName, "module2")) {
          moduleDependencies ++= Seq(
            new dependency(module2JVMMain) {
              isExported := false
            },
            new dependency(module2JVMTest) {
              isExported := false
            }
          )
        }
        lazy val module2JVMMain: module = new module("module2JVM.main", Array(projectName, "module2")) {
          moduleDependencies := Seq(
            new dependency(module2SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
          )
        }
        lazy val module2JVMTest: module = new module("module2JVM.test", Array(projectName, "module2")) {
          moduleDependencies := Seq(
            new dependency(module1JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module1SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2SourcesTest) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }
        lazy val module2Sources: module = new module("module2-sources", Array(projectName, "module2")) {
          moduleDependencies ++= Seq(
            new dependency(module2SourcesMain) {
              isExported := false
            },
            new dependency(module2SourcesTest) {
              isExported := false
            }
          )
        }

        lazy val module2SourcesMain: module = new module("module2-sources.main", Array(projectName, "module2")) {
          moduleDependencies := Seq()
        }

        lazy val module2SourcesTest: module = new module("module2-sources.test", Array(projectName, "module2")) {
          moduleDependencies := Seq(
            new dependency(module1JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module2JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }

        lazy val module3: module = new module(s"$projectName.module3") {
          moduleDependencies ++= Seq(
            new dependency(module3Main) {
              isExported := false
            },
            new dependency(module3Test) {
              isExported := false
            }
          )
        }
        lazy val module3Main: module = new module(s"$projectName.module3.main") {
          moduleDependencies := Seq()
        }
        lazy val module3Test: module = new module(s"$projectName.module3.test") {
          moduleDependencies := Seq(
            new dependency(module2JVMTest) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module1JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module1SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2SourcesTest) {
              isExported := true
              scope := DependencyScope.COMPILE
            },
            new dependency(module2JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module3Main) {
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
          moduleDependencies := Seq(
            new dependency(module2JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module2SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            }
          )
        }
        lazy val rootTest: module = new module(s"$projectName.test") {
          sbtProjectId := "root"
          moduleDependencies := Seq(
            new dependency(rootMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module2JVMMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(module2SourcesMain) {
              isExported := true
              scope := DependencyScope.COMPILE
            }
          )
        }

        modules := Seq(
          module1JS, module1JSMain, module1JSTest,
          module1JVM, module1JVMMain, module1JVMTest,
          module1Sources, module1SourcesMain, module1SourcesTest,
          root, rootMain, rootTest,
          module2JS, module2JSMain, module2JSTest,
          module2JVM, module2JVMMain, module2JVMTest,
          module2Sources, module2SourcesMain, module2SourcesTest,
          module3, module3Main, module3Test
        )
      }
    )
  }

  //noinspection TypeAnnotation
  // SCL-16204, SCL-17597
  def testJavaLanguageLevelAndTargetByteCodeLevel(): Unit = {
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

  //noinspection TypeAnnotation
  // SCL-16204, SCL-17597
  def testJavaLanguageLevelAndTargetByteCodeLevel_NoOptions(): Unit = {
    val projectLangaugeLevel = SbtProjectStructureImportingTest_ProdTestSourcesSeparatedEnabled.this.projectJdkLanguageLevel
    val projectName = "java-language-level-and-target-byte-code-level-no-options"
    def doRunTest(): Unit = runTest(
      new project(projectName) {
        javacOptions := Nil
        javaLanguageLevel := projectLangaugeLevel
        javaTargetBytecodeLevel := null

        def createModule(name: String): module = new module(name) {
          javaLanguageLevel := projectLangaugeLevel
          javaTargetBytecodeLevel := null
          javacOptions := Nil
        }

        val root = createModule(s"$projectName")
        val rootMain = createModule(s"$projectName.main")
        val rootTest = createModule(s"$projectName.test")
        val module1 = createModule(s"$projectName.module1")
        val module1Main = createModule(s"$projectName.module1.main")
        val module1Test = createModule(s"$projectName.module1.test")

        modules := Seq(root, rootMain, rootTest, module1, module1Main, module1Test)
      }
    )

    doRunTest()

    // Emulate User changing the settings manually
    ExternalSystemApiUtil.executeProjectChangeAction(ApplicationManager.getApplication, () => {
      val ManuallySetTarget = "9"
      val ManuallySetSource = LanguageLevel.JDK_1_9

      setOptions(myProject, ManuallySetSource, ManuallySetTarget, Seq("-some-root-option"))

      val projectModules = myProject.modules
      projectModules.foreach(setOptions(_, ManuallySetSource, ManuallySetTarget, Seq("-some-module-option")))
    })

    // Manually set settings should be rewritten if no explicit javac options provided
    doRunTest()
  }

  // noinspection TypeAnnotation
  // because with prod/test sources feature it started to be possible to support different options for
  // Compile and Test scope in IDEA, so I have enriched this test with different options for the Test scope
  def testJavacOptionsPerModuleAndScope(): Unit = {
    val projectName = "javac-options-per-module"
    runTest(new project(projectName) {
      javacOptions := Nil // no storing project level options

      def moduleX(name: String, expectedJavacOptions: Seq[String]): module = new module(s"$projectName.$name") {
        javacOptions := expectedJavacOptions
      }

      // TODO: currently IDEA doesn't support more finely-grained scopes,like `in (Compile, compile)
      //  so option root_option_in_compile_compile is not included
      //  IDEA-232043, SCL-11883, SCL-17020
      val rootModules = Seq(
        new module(projectName),
        moduleX("main", Seq("root_option", "root_option_in_compile")),
        moduleX("test", Seq("root_option", "root_option_in_compile", "root_option_in_test"))
      )

      val modules1 = Seq(
        moduleX("module1", Seq()),
        moduleX("module1.main", Seq("module_1_option", "module_1_option_in_compile")),
        moduleX("module1.test", Seq("module_1_option", "module_1_option_in_compile", "module_1_option_in_test"))
      )

      val modules2 = Seq(
        moduleX("module2", Seq()),
        moduleX("module2.main", Seq("module_2_option", "module_2_option_in_compile")),
        moduleX("module2.test", Seq("module_2_option", "module_2_option_in_compile", "module_2_option_in_test"))
      )

      val modules3 = Seq(
        moduleX("module3", Seq()),
        moduleX("module3.main", Seq()),
        moduleX("module3.test", Seq("module_3_option_in_test"))
      )

      modules := rootModules ++ modules1 ++ modules2 ++ modules3
    }
    )
  }

  // noinspection TypeAnnotation
  def testScalacOptionsPerModuleAndScope(): Unit = {
    val projectName = "scalac-options-per-module"
    runTest(new project(projectName) {
      scalacOptions := Nil // no storing project level options

      def moduleX(name: String, expectedJavacOptions: Seq[String]): module = new module(s"$projectName.$name") {
        scalacOptions := expectedJavacOptions
      }

      // TODO: currently IDEA doesn't support more finely-grained scopes,like `in (Compile, compile)
      //  so option root_option_in_compile_compile is not included
      //  IDEA-232043, SCL-11883, SCL-17020
      val rootModules = Seq(
        new module(projectName),
        moduleX("main", Seq("root_option", "root_option_in_compile")),
        moduleX("test", Seq("root_option", "root_option_in_compile", "root_option_in_test"))
      )

      val modules1 = Seq(
        moduleX("module1", Seq()),
        moduleX("module1.main", Seq("module_1_option", "module_1_option_in_compile")),
        moduleX("module1.test", Seq("module_1_option", "module_1_option_in_compile", "module_1_option_in_test"))
      )

      val modules2 = Seq(
        moduleX("module2", Seq()),
        moduleX("module2.main", Seq("module_2_option", "module_2_option_in_compile")),
        moduleX("module2.test", Seq("module_2_option", "module_2_option_in_compile", "module_2_option_in_test"))
      )

      val modules3 = Seq(
        moduleX("module3", Seq()),
        moduleX("module3.main", Seq()),
        moduleX("module3.test", Seq("module_3_option_in_test"))
      )

      modules := rootModules ++ modules1 ++ modules2 ++ modules3
    }
    )
  }

  def testJavacSpecialOptionsForRootProject(): Unit = {
    runTest(
      new project("javac-special-options-for-root-project") {
        // no storing project level options
        javacOptions := Nil
        javaTargetBytecodeLevel := null
        javaLanguageLevel := SbtProjectStructureImportingTest_ProdTestSourcesSeparatedEnabled.this.projectJdkLanguageLevel

        val root: module = new module("javac-special-options-for-root-project")
        val rootMain: module = new module("javac-special-options-for-root-project.main") {
          javaLanguageLevel := LanguageLevel.JDK_1_9
          javaTargetBytecodeLevel := "1.7"
          javacOptions := Seq(
            "-g:none",
            "-nowarn",
            "-deprecation",
            "-Werror"
          )
        }
        val rootTest: module = new module("javac-special-options-for-root-project.test") {
          javaLanguageLevel := LanguageLevel.JDK_1_9
          javaTargetBytecodeLevel := "1.7"
          javacOptions := Seq(
            "-g:none",
            "-nowarn",
            "-deprecation",
            "-Werror"
          )
        }
        modules:= Seq(root, rootMain, rootTest)
      }
    )

    val compilerOptions = JavacConfiguration.getOptions(myProject, classOf[JavacConfiguration])
    val defaultCompilerOptions = new JpsJavaCompilerOptions

    assertEquals(defaultCompilerOptions.DEBUGGING_INFO, compilerOptions.DEBUGGING_INFO)
    assertEquals(defaultCompilerOptions.GENERATE_NO_WARNINGS, compilerOptions.GENERATE_NO_WARNINGS)
    assertEquals(defaultCompilerOptions.DEPRECATION, compilerOptions.DEPRECATION)
    assertEquals(defaultCompilerOptions.ADDITIONAL_OPTIONS_STRING, compilerOptions.ADDITIONAL_OPTIONS_STRING)
    assertEquals(defaultCompilerOptions.MAXIMUM_HEAP_SIZE, compilerOptions.MAXIMUM_HEAP_SIZE)
    assertEquals(defaultCompilerOptions.PREFER_TARGET_JDK_COMPILER, compilerOptions.PREFER_TARGET_JDK_COMPILER)
  }

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

  def testSimpleProjectWithGeneratedSources(): Unit = runTest(
    new project("SimpleProjectWithGeneratedSources") {
      modules := Seq(
        new module("SimpleProjectWithGeneratedSources") {
          sources := Seq()
          testSources := Seq()
          resources := Seq()
          testResources := Seq()
          excluded := Seq("target")
        },
        new module("SimpleProjectWithGeneratedSources.main") {
          sources := Seq("scala", "", "")
          contentRoots := Seq(
            s"$getProjectPath/src/main",
            s"$getProjectPath/target/myGenerated/main",
            s"$getProjectPath/target/scala-2.13/src_managed/main",
            s"$getProjectPath/target/scala-2.13/resource_managed/main",
          )
          testSources := Seq()
          resources := Seq("resources", "")
          testResources := Seq()
          excluded := Seq()
        },
        new module("SimpleProjectWithGeneratedSources.test") {
          sources := Seq()
          contentRoots := Seq(
            s"$getProjectPath/src/test",
            s"$getProjectPath/target/myGenerated/test",
            s"$getProjectPath/target/scala-2.13/src_managed/test",
            s"$getProjectPath/target/scala-2.13/resource_managed/test",
          )
          testSources := Seq("scala", "", "")
          resources := Seq()
          testResources := Seq("resources", "")
          excluded := Seq()
        },
        new module("SimpleProjectWithGeneratedSources.SimpleProjectWithGeneratedSources-build"),
      )
    }
  )

  def testCustomConfigurationsWithNestedProjectDependencies(): Unit = {
    val projectName = "customConfigurationsWithNestedProjectDependencies"
    runTest(
      new project(projectName) {

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
            }
          )
        }

        lazy val foo: module = new module(s"$projectName.foo") {
          sbtProjectId := "foo"
          moduleDependencies ++= Seq(
            new dependency(fooMain) {
              isExported := false
            },
            new dependency(fooTest) {
              isExported := false
            }
          )
        }
        lazy val fooMain: module = new module(s"$projectName.foo.main") {
          sbtProjectId := "foo"
          moduleDependencies := Seq()
        }
        lazy val fooTest: module = new module(s"$projectName.foo.test") {
          sbtProjectId := "foo"
          moduleDependencies := Seq(
            new dependency(rootMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(fooMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }

        lazy val utils: module = new module(s"$projectName.utils") {
          sbtProjectId := "utils"
          moduleDependencies ++= Seq(
            new dependency(utilsMain) {
              isExported := false
            },
            new dependency(utilsTest) {
              isExported := false
            }
          )
        }
        lazy val utilsMain: module = new module(s"$projectName.utils.main") {
          sbtProjectId := "utils"
          moduleDependencies := Seq(
            new dependency(fooMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }
        lazy val utilsTest: module = new module(s"$projectName.utils.test") {
          sbtProjectId := "utils"
          moduleDependencies := Seq(
            new dependency(fooTest) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(fooMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(utilsMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(rootMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            }
          )
        }
        modules := Seq(
          utils, utilsMain, utilsTest,
          foo, fooMain, fooTest,
          root, rootMain, rootTest
        )
      }
    )
  }

  def testProjectWithModulesWithSameIdsAndNamesWithDifferentCase(): Unit = runTest(
    new project("sameIdsAndNamesWithDifferentCase") {
      modules :=
        createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase") ++
          createModuleWithSourceSet("U_MY_MODULE_ID", Array("sameIdsAndNamesWithDifferentCase", "same module name")) ++
          createModuleWithSourceSet("U_My_Module_Id", Array("sameIdsAndNamesWithDifferentCase", "same module name")) ++
          createModuleWithSourceSet("U_my_module_id", Array("sameIdsAndNamesWithDifferentCase", "same module name")) ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.X_MY_MODULE_ID") ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.X_my_module_id") ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.X_My_Module_Id") ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.Y_My_Module_Name") ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.Y_my_module_name") ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.Y_MY_MODULE_Name") ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.Z_MY_MODULE_Name") ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.Z_My_Module_Name") ++
          createModuleWithSourceSet("sameIdsAndNamesWithDifferentCase.Z_my_module_name")
    }
  )

  def testMultiBuildProjectWithSpecialCharactersInRootProjectNames(): Unit = runTest(
    new project("ro//o/t\\") {
      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

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

  def testSharedSourcesInsideMultiBuildProject(): Unit = runTest(
    new project("sharedSourcesInsideMultiBuildProject") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
      libraries := scalaLibraries

      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

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
        moduleDependencies += new dependency(c1Main) { isExported := false }
      }

      lazy val root: module = new module("sharedSourcesInsideMultiBuildProject") {
        contentRoots := Seq(getProjectPath)
        sbtProjectId := "sharedSourcesInsideMultiBuildProject"
        sbtBuildURI := buildURI
        libraryDependencies := Seq()
        moduleDependencies ++= Seq(
          new dependency(rootMain) {
            isExported := false
          },
          new dependency(rootTest) {
            isExported := false
          }
        )
      }
      lazy val rootMain: module = new module("sharedSourcesInsideMultiBuildProject.main") {
        contentRoots := Seq(s"$getProjectPath/src/main", s"$getProjectPath/target/scala-2.13/src_managed/main", s"$getProjectPath/target/scala-2.13/resource_managed/main")
        sbtProjectId := "sharedSourcesInsideMultiBuildProject"
        sbtBuildURI := buildURI
        libraryDependencies := scalaLibraries
        moduleDependencies += new dependency(c1Main) { isExported := false }
      }
      lazy val rootTest: module = new module("sharedSourcesInsideMultiBuildProject.test") {
        contentRoots := Seq(s"$getProjectPath/src/test", s"$getProjectPath/target/scala-2.13/src_managed/test", s"$getProjectPath/target/scala-2.13/resource_managed/test")
        sbtProjectId := "sharedSourcesInsideMultiBuildProject"
        sbtBuildURI := buildURI
        libraryDependencies := scalaLibraries
        moduleDependencies ++= Seq(
          new dependency(c1Main) { isExported := false },
          new dependency(rootMain) { isExported := false }
        )
      }

      val sharedSourcesModuleInC1: module = new module("c1-sources", Array("c1")) {
        libraryDependencies := Seq()
      }
      val sharedSourcesModuleInC1Main: module = new module("c1-sources.main", Array("c1")) {
        libraryDependencies := scalaLibraries
      }
      val fooInC1: module = new module("foo", Array("c1")) {
        libraryDependencies := Seq()
        sbtProjectId := "foo"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies ++= Seq(
          new dependency(fooMainInC1) {
            isExported := false
          },
          new dependency(fooTestInC1) {
            isExported := false
          }
        )
      }
      lazy val fooMainInC1: module = new module("foo.main", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "foo"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies += new dependency(sharedSourcesModuleInC1Main) { isExported := true }
      }
      lazy val fooTestInC1: module = new module("foo.test", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "foo"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies ++= Seq(
          new dependency(sharedSourcesModuleInC1Main) { isExported := true },
          new dependency(fooMainInC1) { isExported := false }
        )
      }

      val bar: module = new module("bar", Array("c1")) {
        libraryDependencies := Seq()
        sbtProjectId := "bar"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies ++= Seq(
          new dependency(barMain) {
            isExported := false
          },
          new dependency(barTest) {
            isExported := false
          }
        )
      }
      lazy val barMain: module = new module("bar.main", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "bar"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies += new dependency(sharedSourcesModuleInC1Main) { isExported := true }
      }
      lazy val barTest: module = new module("bar.test", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "bar"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies ++= Seq(
          new dependency(sharedSourcesModuleInC1Main) { isExported := true },
          new dependency(barMain) { isExported := false }
        )
      }

      val c1Modules: Seq[module] = Seq(
        c1, c1Main, c1Test,
        sharedSourcesModuleInC1, sharedSourcesModuleInC1Main,
        fooInC1, fooMainInC1, fooTestInC1,
        bar, barMain, barTest
      )
      val rootModules: Seq[module] = Seq(
        root, rootMain, rootTest
      )

      modules := rootModules ++ c1Modules
    }
  )

  // SBT guarantees us that project ids inside builds are unique. In IDEA in the internal module name all "/" are replaced with "_" and it could happen that in one build
  // the name of one project would be e.g. ro/t and the other one would be ro_t and for SBT project ids uniqueness would be maintained but not for IDEA.
  // In the case of such deduplication, IDEA will add a ~<number> suffix to each sbt source set module (main/test) or sbt nested module (the parent module for main/test).
  // It's done by explicitly setting the ModuleNameDeduplicationStrategy.NUMBER_SUFFIX in these modules.
  def testMultiBuildProjectWithTheSameProjectIdFromIDEAPerspective(): Unit = runTest(
    new project("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
      libraries := scalaLibraries

      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

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
          new dependency(c1Main) { isExported := false },
          new dependency(rootMain) { isExported := false },
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

  //SCL-22637
  def testPackagePrefix(): Unit = runTest(
    new project("packagePrefix") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
      libraries := scalaLibraries
      packagePrefix := "com.example"
      lazy val root: module = new module("packagePrefix") {
        contentRoots := Seq(getProjectPath)
        libraryDependencies := Seq()
        moduleDependencies ++= Seq(
          new dependency(rootMain) {
            isExported := false
          },
          new dependency(rootTest) {
            isExported := false
          }
        )
      }
      lazy val rootMain: module = new module("packagePrefix.main") {
        contentRoots := Seq(s"$getProjectPath/src/main", s"$getProjectPath/target/scala-2.13/src_managed/main", s"$getProjectPath/target/scala-2.13/resource_managed/main")
        libraryDependencies := scalaLibraries
      }
      lazy val rootTest: module = new module("packagePrefix.test") {
        contentRoots := Seq(s"$getProjectPath/src/test", s"$getProjectPath/target/scala-2.13/src_managed/test", s"$getProjectPath/target/scala-2.13/resource_managed/test")
        libraryDependencies := scalaLibraries
        moduleDependencies += new dependency(rootMain) { isExported := false }
      }

      modules := Seq(root, rootMain, rootTest)
    }
  )

  def testSimpleSbt2Latest(): Unit = {
    val expectedScala_3_3 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("3.3.3")
    val expectedScala_3_6 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("3.6.2")

    val expectedScalaLibraries = expectedScala_3_3 ++ expectedScala_3_6

    injectVariable(
      getTestProjectDir / "project" / "build.properties",
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
      myProject.modulesWithScala.map(_.getName).sorted,
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )

    assertDirectoryCompletionVariantsForProjectPaths(
      DefaultSbtContentRootsScala3,
      DefaultMainSbtContentRootsScala3,
      DefaultTestSbtContentRootsScala3,
      myProject.baseDir.getPath,
      myProject.baseDir.getPath + "/subProject1",
      myProject.baseDir.getPath + "/subProject2"
    )
  }

  // reduced version of the example project from SCL-23577
  def testProjectIntegrationTestSourcesOutsideContentRoot(): Unit = {
    runTest(
      new project("root") {
        lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
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
            new dependency(subProjectMain) { isExported := false },
            new dependency(subProjectTest) { isExported := false },
            new dependency(subProjectIntegrationTestMain) { isExported := false }
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/subProject/src/it/java",
            "%PROJECT_ROOT%/subProject/src/it/scala",
            "%PROJECT_ROOT%/subProject/src/it/scala-2",
            "%PROJECT_ROOT%/subProject/src/it/scala-2.13",
            "%PROJECT_ROOT%/derived-projects/subProject-integration-test/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/subProject/src/it/resources",
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

  private def createModuleWithSourceSet(moduleName: String, group: Array[String] = null): Seq[module] =
    Seq(moduleName, s"$moduleName.main", s"$moduleName.test").map { name =>
      new module(name, group)
    }
}
