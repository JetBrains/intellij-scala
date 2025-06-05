package org.jetbrains.sbt.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.experimental.categories.Category

import java.net.URI
import java.nio.file.{Files, Path}

// TODO: ensure there is test for SCL-19673 for BSO external system as well
/**
 * @see [[SbtProjectStructureImportingTest]]
 */
@Category(Array(classOf[SlowTests]))
final class SbtSharedSourcesProjectStructureTest_ProdTestSourcesSeparatedEnabled extends SbtProjectStructureImportingLike {

  import ProjectStructureDsl._

  override protected def enableSeparateModulesForProdTest = true

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

  def testCrossPlatformPureInRoot(): Unit = {
    def javaContentRoots(sourceSet: String): Seq[String] =
      Seq(
        s"%PROJECT_ROOT%/src/$sourceSet/java",
        s"%PROJECT_ROOT%/target/scala-2.11/resource_managed/$sourceSet",
        s"%PROJECT_ROOT%/target/scala-2.11/src_managed/$sourceSet"
      )

    def platformContentRoots(platform: String, sourceSet: String): Seq[String] =
      Seq(
       s"%PROJECT_ROOT%/.$platform/src/$sourceSet",
       s"%PROJECT_ROOT%/.$platform/target/scala-2.11/resource_managed/$sourceSet",
       s"%PROJECT_ROOT%/.$platform/target/scala-2.11/src_managed/$sourceSet"
      )

    runTest(
      new project("crossplatformpureinroot") {
        val sharedModule: module = new module("crossplatformpureinroot.crossPlatformPureInRoot-sources") {
          sources := Nil
          testSources := Nil
          contentRoots := Nil
          emptySourceResourceDirs(this)
        }
        val sharedModuleMain: module = new module("crossplatformpureinroot.crossPlatformPureInRoot-sources.main") {
          contentRoots := Seq(s"%PROJECT_ROOT%/src/main")
          sources := Seq(s"%PROJECT_ROOT%/src/main/scala")
          emptySourceResourceDirsTest(this)
        }
        val sharedModuleTest: module = new module("crossplatformpureinroot.crossPlatformPureInRoot-sources.test") {
          testSources := Seq("%PROJECT_ROOT%/src/test/scala")
          contentRoots += "%PROJECT_ROOT%/src/test"
          emptySourceResourceDirsMain(this)
        }

        val jvmModule: module = new module("crossplatformpureinroot.root.rootJVM") {
          contentRoots += "%PROJECT_ROOT%/.jvm"
          excluded := Seq("%PROJECT_ROOT%/.jvm/target")
          emptySourceResourceDirs(this)
        }
        val jvmModuleMain: module = new module("crossplatformpureinroot.root.rootJVM.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          contentRoots := platformContentRoots("jvm", "main")
          sources := Seq("%PROJECT_ROOT%/.jvm/src/main/scala")
          emptySourceResourceDirsTest(this)
        }
        val jvmModuleTest: module = new module("crossplatformpureinroot.root.rootJVM.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jvmModuleMain) { isExported := false }
          )
          contentRoots := platformContentRoots("jvm", "test")
          testSources := Seq("%PROJECT_ROOT%/.jvm/src/test/scala")
          emptySourceResourceDirsMain(this)
        }

        val jsModule: module = new module("crossplatformpureinroot.root.rootJS") {
          contentRoots += "%PROJECT_ROOT%/.js"
          excluded := Seq("%PROJECT_ROOT%/.js/target")
          emptySourceResourceDirs(this)
        }
        val jsModuleMain: module = new module("crossplatformpureinroot.root.rootJS.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          contentRoots := platformContentRoots("js", "main")
          sources := Seq("%PROJECT_ROOT%/.js/src/main/scala")
          emptySourceResourceDirsTest(this)
        }
        val jsModuleTest: module = new module("crossplatformpureinroot.root.rootJS.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(jsModuleMain) { isExported := false }
          )
          contentRoots := platformContentRoots("js", "test")
          testSources := Seq("%PROJECT_ROOT%/.js/src/test/scala")
          emptySourceResourceDirsMain(this)
        }

        val root: module = new module("crossplatformpureinroot") {
          contentRoots += "%PROJECT_ROOT%"
          excluded := Seq("%PROJECT_ROOT%/target")
          emptySourceResourceDirs(this)
        }
        val rootMain: module = new module("crossplatformpureinroot.main") {
          contentRoots := javaContentRoots("main")
          sources := Seq("%PROJECT_ROOT%/src/main/java")
          emptySourceResourceDirsTest(this)
        }
        val rootTest: module = new module("crossplatformpureinroot.test") {
          contentRoots := javaContentRoots("test")
          emptySourceResourceDirs(this)
        }

        modules := Seq(
          sharedModule, sharedModuleMain, sharedModuleTest,
          root, rootMain, rootTest,
          jvmModule, jvmModuleMain, jvmModuleTest,
          jsModule, jsModuleMain, jsModuleTest
        )
      }
    )

    buildCrossProjectAndAssertNoWarningsOrErrors()
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

  // A similar case is present in https://github.com/scala/scala3
  def testTheSameSourceBaseDirsInDifferentProjects(): Unit =
    runTest(
      new project("root") {
        lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
        libraries := scalaLibraries

        lazy val root: module = new module("root") {
          contentRoots := Seq("%PROJECT_ROOT%")
          excluded := Seq("%PROJECT_ROOT%/target")
          libraryDependencies := Nil
          moduleDependencies ++= Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false }
          )
          emptySourceResourceDirs(this)
        }
        lazy val rootMain: module = new module("root.main") {
          libraryDependencies := scalaLibraries
          moduleDependencies := Seq(new dependency(fooSourcesMain) { isExported := true })
          contentRoots := Seq(
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/main",
          )
          emptySourceResourceDirs(this)
        }
        lazy val rootTest: module = new module("root.test") {
          libraryDependencies := scalaLibraries
          moduleDependencies ++= Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(fooSourcesMain) { isExported := true }
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/src/test",
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/test"
          )
          emptySourceResourceDirs(this)
        }

        lazy val foo: module = new module("root.foo") {
          contentRoots := Seq("%PROJECT_ROOT%/foo")
          excluded := Seq("%PROJECT_ROOT%/foo/target")
          libraryDependencies := Nil
          moduleDependencies ++= Seq(
            new dependency(fooMain) { isExported := false },
            new dependency(fooTest) { isExported := false }
          )
          emptySourceResourceDirs(this)
        }
        lazy val fooMain: module = new module("root.foo.main") {
          libraryDependencies := scalaLibraries
          moduleDependencies := Seq(new dependency(fooSourcesMain) { isExported := true })
          contentRoots := Seq(
            "%PROJECT_ROOT%/foo/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/foo/target/scala-2.13/resource_managed/main",
          )
          emptySourceResourceDirs(this)
        }
        lazy val fooTest: module = new module("root.foo.test") {
          libraryDependencies := scalaLibraries
          moduleDependencies ++= Seq(
            new dependency(fooMain) { isExported := false },
            new dependency(fooSourcesMain) { isExported := true }
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/foo/src/test",
            "%PROJECT_ROOT%/foo/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/foo/target/scala-2.13/resource_managed/test"
          )
          emptySourceResourceDirs(this)
        }

        lazy val fooSources: module = new module("root.foo-sources") {
          contentRoots := Seq()
          excluded := Seq()
          libraryDependencies := Nil
          moduleDependencies ++= Seq(
            new dependency(fooSourcesMain) { isExported := false },
          )
          emptySourceResourceDirs(this)
        }
        lazy val fooSourcesMain: module = new module("root.foo-sources.main") {
          libraryDependencies := scalaLibraries
          moduleDependencies := Nil
          contentRoots := Seq("%PROJECT_ROOT%/foo/src/main")
          emptySourceResourceDirs(this)
        }

        modules := Seq(root, rootMain, rootTest, foo, fooMain, fooTest, fooSources, fooSourcesMain)
      }
    )

  // the test case extracted from https://youtrack.jetbrains.com/issue/SCL-23789
  def testSharedSourcesInProjectBase(): Unit = {
    runTest(
      new project("root") {
        val sharedModuleMain: module = new module("root.sharedSourcesInProjectBase-sources.main") {
          contentRoots += "%PROJECT_ROOT%/src/main"
          sources := Seq("buzz", "buzz-2", "buzz-2.13")
        }
        val sharedModule: module = new module("root.sharedSourcesInProjectBase-sources") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := false }
          contentRoots := Nil
        }

        val sharedMain: module = new module("root.shared.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          contentRoots := standardRoots("src/main/buzz", "main")
          emptySourceResourceDirs(this)
        }
        val sharedTest: module = new module("root.shared.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedMain) { isExported := false }
          )
          contentRoots := standardRoots("src/main/buzz", "test")
          emptySourceResourceDirs(this)
        }
        val shared: module = new module("root.shared") {
          moduleDependencies := Seq(
            new dependency(sharedMain) { isExported := false },
            new dependency(sharedTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%/src/main/buzz"
          excluded += "target"
        }

        val fooMain: module = new module("root.foo.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          contentRoots := standardRoots("src/main/buzz/foo", "main")
          emptySourceResourceDirs(this)
        }
        val fooTest: module = new module("root.foo.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(fooMain) { isExported := false }
          )
          contentRoots := standardRoots("src/main/buzz/foo", "test")
          emptySourceResourceDirs(this)
        }
        val foo: module = new module("root.foo") {
          moduleDependencies := Seq(
            new dependency(fooMain) { isExported := false },
            new dependency(fooTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%/src/main/buzz/foo"
          excluded += "target"
        }

        val rootMain: module = new module("root.main") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedMain) { isExported := false }
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/src/main/java",
            "%PROJECT_ROOT%/src/main/scala",
            "%PROJECT_ROOT%/src/main/scala-2",
            "%PROJECT_ROOT%/src/main/scala-2.13",
            "%PROJECT_ROOT%/src/main/resources",
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/main",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/main"
          )
          sources += "%PROJECT_ROOT%/src/main/scala"
        }
        val rootTest: module = new module("root.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(sharedMain) { isExported := false },
            new dependency(rootMain) { isExported := false }
          )
          contentRoots := Seq(
            "%PROJECT_ROOT%/src/test",
            "%PROJECT_ROOT%/target/scala-2.13/src_managed/test",
            "%PROJECT_ROOT%/target/scala-2.13/resource_managed/test"
          )
          emptySourceResourceDirs(this)
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
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(fooMain) { isExported := false }
          )
          contentRoots := standardRoots("dummy", "main")
          sources += "%PROJECT_ROOT%/dummy/src/main/scala"
        }
        val dummyTest: module = new module("root.dummy.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(dummyMain) { isExported := false },
            new dependency(fooMain) { isExported := false }
          )
          contentRoots := standardRoots("dummy", "test")
          emptySourceResourceDirs(this)
        }
        val dummy: module = new module("root.dummy") {
          moduleDependencies := Seq(
            new dependency(dummyMain) { isExported := false },
            new dependency(dummyTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%/dummy"
          excluded := Seq("target")
        }
        modules := Seq(
          root, rootMain, rootTest,
          foo, fooMain, fooTest,
          dummy, dummyMain, dummyTest,
          sharedModuleMain, sharedModule,
          sharedMain, sharedTest, shared
        )
      }
    )
  }

  // test a case in which a shared directory is not under standard src/main or src/test
  def testSharedSourcesInNonStandardDirectory(): Unit = {
    runTest(
      new project("root") {
        val sharedModuleTest: module = new module("root.dummy-sources.test") {
          contentRoots := Seq("%PROJECT_ROOT%/dummy/src")
          testSources := Seq("%PROJECT_ROOT%/dummy/src")
          emptySourceResourceDirsMain(this)
        }
        val sharedModule: module = new module("root.dummy-sources") {
          moduleDependencies += new dependency(sharedModuleTest) { isExported := false }
          contentRoots := Nil
        }

        val buzzMain: module = new module("root.buzz.main") {
          moduleDependencies := Nil
          contentRoots := standardRoots("buzz", "main", "3.0.2")
          emptySourceResourceDirs(this)
        }
        val buzzTest: module = new module("root.buzz.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(buzzMain) { isExported := false }
          )
          contentRoots := standardRoots("buzz", "test", "3.0.2")
          testSources := Seq("%PROJECT_ROOT%/buzz/src/test/scala")
          emptySourceResourceDirsMain(this)
        }
        val buzz: module = new module("root.buzz") {
          moduleDependencies := Seq(
            new dependency(buzzMain) { isExported := false },
            new dependency(buzzTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%/buzz"
          excluded += "target"
        }

        val fooMain: module = new module("root.foo.main") {
          moduleDependencies := Nil
          contentRoots := standardRoots("foo", "main", "3.0.2")
          emptySourceResourceDirs(this)
        }
        val fooTest: module = new module("root.foo.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(fooMain) { isExported := false }
          )
          contentRoots := standardRoots("foo", "test", "3.0.2")
          testSources := Seq("%PROJECT_ROOT%/foo/src/test/scala")
          emptySourceResourceDirsMain(this)
        }
        val foo: module = new module("root.foo") {
          moduleDependencies := Seq(
            new dependency(fooMain) { isExported := false },
            new dependency(fooTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%/foo"
          excluded += "target"
        }

        val rootMain: module = new module("root.main") {
          moduleDependencies := Nil
          contentRoots := standardRoots("", "main", "3.0.2")
          emptySourceResourceDirs(this)
        }
        val rootTest: module = new module("root.test") {
          moduleDependencies := Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(buzzMain) { isExported := false },
            new dependency(buzzTest) { isExported := false },
            new dependency(sharedModuleTest) { isExported := true },
          )
          contentRoots := standardRoots("", "test", "3.0.2")
          testSources += "%PROJECT_ROOT%/src/test/scala"
          emptySourceResourceDirsMain(this)
        }
        val root: module = new module("root") {
          moduleDependencies := Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%"
          excluded += "target"
        }

        modules := Seq(
          root, rootMain, rootTest,
          foo, fooMain, fooTest,
          buzz, buzzMain, buzzTest,
          sharedModule, sharedModuleTest,
        )
      }
    )
    buildCrossProjectAndAssertNoWarningsOrErrors()
    assertNoTargetDirGeneratedInSharedDirectory("%PROJECT_ROOT%/dummy")
  }

  // Test case where shared directories are not located under the standard src/main or src/test paths,
  // and are in completely different directories (while still grouped within the same shared directory module).
  def testSharedSourcesInNotRelatedDirs(): Unit = {
    runTest(
      new project("root") {
        val sharedModuleTest: module = new module("root.shared-sources.test") {
          contentRoots := Seq("%PROJECT_ROOT%/dummy/src")
          testSources := Seq("%PROJECT_ROOT%/dummy/src")
          emptySourceResourceDirsMain(this)
        }
        val sharedModuleMain: module = new module("root.shared-sources.main") {
          contentRoots := Seq("%PROJECT_ROOT%/nothing/dummy")
          sources := Seq("%PROJECT_ROOT%/nothing/dummy")
          emptySourceResourceDirsTest(this)
        }
        val sharedModule: module = new module("root.shared-sources") {
          moduleDependencies ++= Seq(
            new dependency(sharedModuleTest) { isExported := false },
            new dependency(sharedModuleMain) { isExported := false },
          )
          contentRoots := Nil
        }

        val buzzMain: module = new module("root.buzz.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          contentRoots := standardRoots("buzz", "main", "3.0.2")
          sources := Seq("%PROJECT_ROOT%/buzz/src/main/scala")
          emptySourceResourceDirsTest(this)
        }
        val buzzTest: module = new module("root.buzz.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(buzzMain) { isExported := false }
          )
          contentRoots := standardRoots("buzz", "test", "3.0.2")
          testSources := Seq("%PROJECT_ROOT%/buzz/src/test/scala")
          emptySourceResourceDirsMain(this)
        }
        val buzz: module = new module("root.buzz") {
          moduleDependencies := Seq(
            new dependency(buzzMain) { isExported := false },
            new dependency(buzzTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%/buzz"
          excluded += "target"
        }

        val fooMain: module = new module("root.foo.main") {
          moduleDependencies += new dependency(sharedModuleMain) { isExported := true }
          contentRoots := standardRoots("foo", "main", "3.0.2")
          sources := Seq("%PROJECT_ROOT%/foo/src/main/scala")
          emptySourceResourceDirsTest(this)
        }
        val fooTest: module = new module("root.foo.test") {
          moduleDependencies := Seq(
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(sharedModuleMain) { isExported := true },
            new dependency(fooMain) { isExported := false }
          )
          contentRoots := standardRoots("foo", "test", "3.0.2")
          testSources := Seq("%PROJECT_ROOT%/foo/src/test/scala")
          emptySourceResourceDirsMain(this)
        }
        val foo: module = new module("root.foo") {
          moduleDependencies := Seq(
            new dependency(fooMain) { isExported := false },
            new dependency(fooTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%/foo"
          excluded += "target"
        }

        val rootMain: module = new module("root.main") {
          moduleDependencies := Nil
          contentRoots := standardRoots("", "main", "3.0.2")
          emptySourceResourceDirs(this)
        }
        val rootTest: module = new module("root.test") {
          moduleDependencies := Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(buzzMain) { isExported := false },
            new dependency(buzzTest) { isExported := false },
            new dependency(sharedModuleTest) { isExported := true },
            new dependency(sharedModuleMain) { isExported := true },
          )
          contentRoots := standardRoots("", "test", "3.0.2")
          testSources += "%PROJECT_ROOT%/src/test/scala"
          emptySourceResourceDirsMain(this)
        }
        val root: module = new module("root") {
          moduleDependencies := Seq(
            new dependency(rootMain) { isExported := false },
            new dependency(rootTest) { isExported := false },
          )
          contentRoots += "%PROJECT_ROOT%"
          excluded += "target"
        }

        modules := Seq(
          root, rootMain, rootTest,
          foo, fooMain, fooTest,
          buzz, buzzMain, buzzTest,
          sharedModule, sharedModuleTest, sharedModuleMain
        )
      }
    )
    buildCrossProjectAndAssertNoWarningsOrErrors()
    assertNoTargetDirGeneratedInSharedDirectory("%PROJECT_ROOT%/dummy")
  }
}
