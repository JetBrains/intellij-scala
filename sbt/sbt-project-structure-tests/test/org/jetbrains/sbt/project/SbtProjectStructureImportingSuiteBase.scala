package org.jetbrains.sbt.project

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.DependencyScope
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.external.JdkByName
import org.jetbrains.sbt.project.ProjectStructureTestUtils.expectedScalaSdkLibraryFromCoursier
import org.jetbrains.sbt.{Sbt, SbtVersion}
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import java.net.URI

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

  //noinspection TypeAnnotation
  // SCL-16204, SCL-17597
  def testJavaLanguageLevelAndTargetByteCodeLevel_NoOptions(): Unit = {
    val projectLanguageLevel = SbtProjectStructureImportingSuiteBase.this.projectJdkLanguageLevel
    val projectName = "java-language-level-and-target-byte-code-level-no-options"
    importJavaLanguageLevelNoOptionsProject(projectLanguageLevel, projectName)

    // Emulate User changing the settings manually
    emulateManualJavaLanguageLevelOptionsChange()

    // Manually set settings should be rewritten if no explicit javac options provided
    importJavaLanguageLevelNoOptionsProject(projectLanguageLevel, projectName)
  }

  private def emulateManualJavaLanguageLevelOptionsChange(): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(ApplicationManager.getApplication, () => {
      val ManuallySetTarget = "9"
      val ManuallySetSource = LanguageLevel.JDK_1_9

      setOptions(getMyProject, ManuallySetSource, ManuallySetTarget, Seq("-some-root-option"))

      val projectModules = getMyProject.modules
      projectModules.foreach(setOptions(_, ManuallySetSource, ManuallySetTarget, Seq("-some-module-option")))
    })

  private def importJavaLanguageLevelNoOptionsProject(projectLanguageLevel: LanguageLevel, projectName: String): Unit = runTest(
    new project(projectName) {
      javacOptions := Nil
      javaLanguageLevel := projectLanguageLevel
      javaTargetBytecodeLevel := null

      def createModule(name: String): module = new module(name) {
        javaLanguageLevel := projectLanguageLevel
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
        javaLanguageLevel := SbtProjectStructureImportingSuiteBase.this.projectJdkLanguageLevel

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

    val compilerOptions = JavacConfiguration.getOptions(getMyProject, classOf[JavacConfiguration])
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
            new dependency(fooMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(rootMain) {
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
            new dependency(utilsMain) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(fooTest) {
              isExported := false
              scope := DependencyScope.COMPILE
            },
            new dependency(fooMain) {
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

  //SCL-22637
  def testPackagePrefix(): Unit = runTest(
    new project("packagePrefix") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")
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
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  protected def customSbtContentRootsForMain(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.SOURCE),
      ("scala", JavaSourceRootType.SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.SOURCE),
      ("resources", JavaResourceRootType.RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  protected def customSbtContentRootsForTest(binaryVersion: Int): Seq[ExpectedDirectoryCompletionVariant] =
    Seq(
      ("java", JavaSourceRootType.TEST_SOURCE),
      ("scala", JavaSourceRootType.TEST_SOURCE),
      (s"scala-2.$binaryVersion", JavaSourceRootType.TEST_SOURCE),
      ("resources", JavaResourceRootType.TEST_RESOURCE),
    ).map(ExpectedDirectoryCompletionVariant.apply.tupled)

  def testSimpleSbt1313(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")

    runSimpleTest("simple", "2.13", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = customSbtContentRootsForParent(13),
      expectedSbtCompletionVariantsForMainModule = customSbtContentRootsForMain(13),
      expectedSbtCompletionVariantsForTestModule = customSbtContentRootsForTest(13)
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    Assert.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      getMyProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    Assert.assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.getProcessOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  def testSimpleSbt149(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true, buildReposOverridden = overrideBuildRepositories)("2.13.14")

    runSimpleTest("simple", "2.13", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = DefaultSbtContentRootsScala213,
      expectedSbtCompletionVariantsForMainModule = DefaultMainSbtContentRootsScala213,
      expectedSbtCompletionVariantsForTestModule = DefaultTestSbtContentRootsScala213
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    Assert.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      getMyProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    Assert.assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.getProcessOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  /**
   * SCL-13600: generate all modules when there is a duplicate project id in the sbt build
   * due to references to different builds, or multiple sbt projects being imported independently from IDEA
   */
  def testSCL13600(): Unit = runTest(
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

  def testMultiBuildProjectWithSpecialCharactersInRootProjectNames(): Unit = runTest(
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

  // SBT guarantees us that project ids inside builds are unique. In IDEA in the internal module name all "/" are replaced with "_" and it could happen that in one build
  // the name of one project would be e.g. ro/t and the other one would be ro_t and for SBT project ids uniqueness would be maintained but not for IDEA.
  // In the case of such deduplication, IDEA will add a ~<number> suffix to each sbt source set module (main/test) or sbt nested module (the parent module for main/test).
  // It's done by explicitly setting the ModuleNameDeduplicationStrategy.NUMBER_SUFFIX in these modules.
  def testMultiBuildProjectWithTheSameProjectIdFromIDEAPerspective(): Unit = runTest(
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

  // Verifies the import process with `-addPluginSbtFile`.
  // It has two builds because the sbt bug (https://github.com/sbt/sbt/issues/8570) fixed in 1.12.1 and 2.0.0-RC9 is related to multi-build setup.
  def testSimpleTwoBuilds_sbt_1_12_1(): Unit = {
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

  // Verifies the import process with `-addPluginSbtFile`
  // It has two builds because the sbt bug (https://github.com/sbt/sbt/issues/8570) fixed in 1.12.1 and 2.0.0-RC9 is related to multi-build setup.
  @RequiresJdk(LanguageLevel.JDK_17)
  def testSimpleTwoBuilds_sbt_2_0_0_RC9(): Unit = {
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

  def testBspDisabledProject(): Unit = {
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

  @RequiresJdk(LanguageLevel.JDK_17)
  def testBspDisabledProject_sbt_2_0_0_RC9(): Unit = {
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

  def testScalafixConfigDisabled(): Unit = {
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

  def testBspDisabledConfigLevel(): Unit = {
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

  // When managed scalaInstance is disabled (SCL-24321), sbt behaves differently depending on the version:
  // - sbt < 1.12.0: throws "Missing Scala tool configuration", which sbt-structure silently ignores
  //   (see https://github.com/JetBrains/sbt-structure/commit/92d78ea4b4fe7dbb48e586751f957d420136a809)
  // - sbt >= 1.12.0: returns a scalaInstance with version 0.0.0 and no jars, which sbt-structure filters out
  //   (see https://github.com/JetBrains/sbt-structure/commit/ff960b9e7c2ff801652881d4482dab197666e7b9)
  // In both cases the project is still imported.
  // See https://www.scala-sbt.org/1.x/docs/Configuring-Scala.html#Configuring+Scala+tool+dependencies
  def testManagedScalaInstanceOff(): Unit = runTest(
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
}
