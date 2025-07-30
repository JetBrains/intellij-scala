package org.jetbrains.sbt.project

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.{Sbt, SbtVersion}
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import java.net.URI

// TODO: ensure there is test for SCL-19673 for BSO external system as well
/**
 * @see [[SbtProjectStructureImportingTest]]
 */
@Category(Array(classOf[SlowTests]))
abstract class SbtProjectStructureImportingTestBase_ProdTestSourcesSeparated extends SbtProjectStructureImportingLike {

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
      myProject.baseDir.getPath
    )
  }

  protected def assertDirectoryCompletionVariantsForProjectPaths(
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

  def testLibraryDependenciesOrder(): Unit = {
    val expectedProject: project = new project("libraryDependenciesOrder") {
      val scalaLibraries: Seq[dependency[library]] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14").map { library =>
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
          new dependency(apiMain) {
            isExported := false
            scope := DependencyScope.COMPILE
          },
          new dependency(apiTest) {
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

  def testUnmanagedDependency(): Unit = runTest(
    new project("unmanagedDependency") {
      val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
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

  //noinspection TypeAnnotation
  // SCL-16204, SCL-17597
  def testJavaLanguageLevelAndTargetByteCodeLevel_NoOptions(): Unit = {
    val projectLangaugeLevel = SbtProjectStructureImportingTestBase_ProdTestSourcesSeparated.this.projectJdkLanguageLevel
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
        javaLanguageLevel := SbtProjectStructureImportingTestBase_ProdTestSourcesSeparated.this.projectJdkLanguageLevel

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
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
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
        lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
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

  private def simpleSbtIvyBasedTest(mutedNotificationTitles: Seq[String] = Seq.empty): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkFromIvy(useEnv = true)("2.12.10")

    runSimpleTest("simple", "2.12", scalaLibraries,
      expectedSbtCompletionVariantsForParentModule = customSbtContentRootsForParent(12),
      expectedSbtCompletionVariantsForMainModule = customSbtContentRootsForMain(12),
      expectedSbtCompletionVariantsForTestModule = customSbtContentRootsForTest(12),
      mutedNotificationTitles = mutedNotificationTitles
    )

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    Assert.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple.test", "simple.main"),
      myProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    Assert.assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  def testSimpleSbt013(): Unit = {
    simpleSbtIvyBasedTest(mutedNotificationTitles = Seq("Legacy sbt version 0.13.18 detected"))
  }

  def testSimpleSbt104(): Unit = {
    simpleSbtIvyBasedTest()
  }

  def testSimpleSbt116(): Unit = {
    simpleSbtIvyBasedTest()
  }

  def testSimpleSbt128(): Unit = {
    simpleSbtIvyBasedTest()
  }

  def testSimpleSbt1313(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")

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
      myProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    Assert.assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  def testSimpleSbt149(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")

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
      myProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    Assert.assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }
}
