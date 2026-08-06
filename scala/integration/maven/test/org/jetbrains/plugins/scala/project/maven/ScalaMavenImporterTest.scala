package org.jetbrains.plugins.scala.project.maven

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.{DependencyScope, ProjectRootManager}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.maven.MavenProjectStructureTestUtils.*
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureDsl.*
import org.jetbrains.sbt.project.ProjectStructureAssertionsFixture
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.{Assert, Test}

import java.nio.file.{Files, Path}
import scala.compiletime.uninitialized

//noinspection ApiStatus
@Category(Array(classOf[SlowTests]))
@RunWith(classOf[JUnit4])
abstract class ScalaMavenImporterTest
  extends MavenImportingTestCase {

  override protected def runInDispatchThread(): Boolean = false

  /** None means use whatever default JDK is chosen by IDEA (most probably internal IDEA JDK) */
  protected def projectJdkVersion: Option[LanguageLevel]

  private var jdk: Sdk = uninitialized

  override protected def setUp(): Unit = {
    super.setUp()

    EdtTestUtil.runInEdtAndWait { () =>
      projectJdkVersion.foreach { jdkVersion =>
        inWriteAction {
          jdk = SmartJDKLoader.getOrCreateJDK(jdkVersion)
          ProjectRootManager.getInstance(getProject).setProjectSdk(jdk)
        }
      }
    }
  }

  override protected def tearDown(): Unit = {
    EdtTestUtil.runInEdtAndWait { () =>
      if (jdk != null) {
        val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
        inWriteAction(jdkTable.removeJdk(jdk))
      }
    }

    super.tearDown()
  }

  override protected def setUpFixtures(): Unit = {
    val fixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(projectName, useDirectoryBasedProjectFormat()).getFixture
    setTestFixture(fixture)
    fixture.setUp()
  }

  private def projectName: String = getTestName(true)

  private def getTestProjectDir: Path = {
    val testDataPath = Path.of(TestUtils.getTestDataPath)
      .getParent.getParent
      .resolve("integration").resolve("maven").resolve("testdata").resolve("maven").resolve("projects")
      .resolve(projectName)
    assert(Files.exists(testDataPath), s"testdata directory not found: $testDataPath")
    testDataPath
  }

  private def getTestProjectDirVFile: VirtualFile =
    VirtualFileManager.getInstance().findFileByNioPath(getTestProjectDir)

  protected def runImportingTest(expected: project): Unit = {
    val pomFile = getTestProjectDir.resolve("pom.xml")

    val pomVFile = VirtualFileManager.getInstance().findFileByNioPath(pomFile)
    Assert.assertNotNull("can't find 'pom.xml' file", pomVFile)

    importProjects(pomVFile)

    new ProjectStructureAssertionsFixture(getProject).assertProjectsEqual(expected)
  }

  private def runImportingTest_Common(
    expectedProjectName: String,
    expectedModuleName: String,
    expectedSourceDirectories: Seq[String],
    expectedTestSourceDirectories: Seq[String],
    expectedLibraries: Seq[library],
  ): Unit =
    runImportingTest(new project(expectedProjectName) {
      libraries := expectedLibraries
      modules := Seq(new module(expectedModuleName) {
        contentRoots := Seq(FileUtil.toSystemIndependentName(getTestProjectDirVFile.toNioPath.toCanonicalPath.toString))
        sources := expectedSourceDirectories
        testSources := expectedTestSourceDirectories
        resources := Seq("src/main/resources")
        testResources := Seq("src/test/resources")
        excluded := Seq("target")
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
        compileOrder := CompileOrder.Mixed
      })
    })

  @Test
  def withScala2(): Unit =
    runImportingTest_Common(
      "withScala2",
      "projectWithScala2",
      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(MavenScalaLibrary(Scala_2_13_6), MavenScalaSdk(Scala_2_13_6))
    )

  @Test
  def withTwoModulesWithScala2And3(): Unit = {
    runImportingTest(new project("withTwoModulesWithScala2And3") {
      val mavenSdkScala2_13: library = MavenScalaSdk(Scala_2_13_6)
      val mavenLibraryScala2_13: library = MavenScalaLibrary(Scala_2_13_6)
      val mavenScalaSdkScala3_1: library = MavenScalaSdk(Scala_3_1_0)
      val mavenLibraryScala3_1: library = MavenScalaLibrary(Scala_3_1_0)
      val testProjectRoot: String = FileUtil.toSystemIndependentName(getTestProjectDirVFile.toNioPath.toCanonicalPath.toString)
      libraries := Seq(mavenSdkScala2_13, mavenLibraryScala2_13, mavenLibraryScala3_1, mavenScalaSdkScala3_1)
      modules := Seq(
        new module("projectWithTwoModulesWithScala2And3") {
          contentRoots := Seq(testProjectRoot)
          sources := Seq()
          testSources := Seq()
          resources := Seq()
          testResources := Seq()
          excluded := Seq("target")
          libraryDependencies := Seq(mavenSdkScala2_13, mavenLibraryScala2_13).map(library2libraryDependency)
          compileOrder := CompileOrder.Mixed
        },
        new module("scala3") {
          contentRoots := Seq(s"$testProjectRoot/scala3")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Seq("target")
          libraryDependencies := Seq(mavenScalaSdkScala3_1, mavenLibraryScala3_1, mavenLibraryScala2_13).map(library2libraryDependency)
          compileOrder := CompileOrder.Mixed
        }
      )
    })
  }

  @Test
  def withScala2_WithExplicitSourceDirectoriesSet(): Unit =
    runImportingTest_Common(
      "withScala2_WithExplicitSourceDirectoriesSet",
      "projectWithScala2",
      //When Maven build has explicit source dirs (sourceDirectory, testSourceDirectory),
      //default java source dirs are replaced
      Seq("src/main/scala"),
      Seq("src/test/scala"),
      Seq(MavenScalaLibrary(Scala_2_13_6), MavenScalaSdk(Scala_2_13_6))
    )

  @Test
  def withScala2_WithoutScalaMavenPlugin(): Unit =
    runImportingTest_Common(
      "withScala2_WithoutScalaMavenPlugin",
      "projectWithScala2",
      Seq("src/main/java"),
      Seq("src/test/java"),
      Seq(MavenScalaLibrary(Scala_2_13_6))
    )

  @Test
  def withScala3_0(): Unit = {
    runImportingTest_Common(
      "withScala3_0",
      "projectWithScala3_0",

      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(
        MavenScalaLibrary(Scala_2_13_6),
        MavenScalaLibrary(Scala_3_0_2),
        MavenScalaSdk(Scala_3_0_2)
      )
    )
  }

  @Test
  def withScala3_1(): Unit = {
    runImportingTest_Common(
      "withScala3_1",
      "projectWithScala3_1",
      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(
        MavenScalaLibrary(Scala_2_13_6),
        MavenScalaLibrary(Scala_3_1_0),
        MavenScalaSdk(Scala_3_1_0),
      )
    )
  }

  private val CommonLibrariesForImplicitScalaLibraryDependencyTests = Seq(
    new library("Maven: junit:junit:4.13.1"),
    new library("Maven: org.hamcrest:hamcrest-core:1.3"),
    new library("Maven: org.scala-lang.modules:scala-xml_2.13:2.0.1"),
    new library("Maven: org.scala-lang:scala-reflect:2.13.6"),
    new library("Maven: org.scala-sbt:test-interface:1.0"),
    new library("Maven: org.scalactic:scalactic_2.13:3.2.11"),
    new library("Maven: org.scalameta:junit-interface:0.7.25"),
    new library("Maven: org.scalameta:munit_2.13:0.7.25"),
    new library("Maven: org.scalatest:scalatest-compatible:3.2.11"),
    new library("Maven: org.scalatest:scalatest-core_2.13:3.2.11"),
  )

  @Test
  def withImplicitScalaLibraryDependency_compilerVersionLargest(): Unit = {
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6),
      MavenScalaSdk(Scala_2_13_14)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("withImplicitScalaLibraryDependency_compilerVersionLargest") {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })
  }

  @Test
  def withImplicitScalaLibraryDependency_compilerVersionInTheMiddle(): Unit = {
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6),
      MavenScalaSdk(Scala_2_13_5)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("withImplicitScalaLibraryDependency_compilerVersionInTheMiddle") {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })
  }

  @Test
  def withImplicitScalaLibraryDependency_compilerVersionSmallest(): Unit = {
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6),
      MavenScalaSdk(Scala_2_13_0)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("withImplicitScalaLibraryDependency_compilerVersionSmallest") {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })
  }

  @Test
  def withImplicitScalaLibraryDependency_compilerVersionSmallest_LibraryDependenciesHaveTestScope(): Unit = {
    val expectedCompileLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_0),
      MavenScalaSdk(Scala_2_13_0)
    )

    val expectedTestLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("withImplicitScalaLibraryDependency_compilerVersionSmallest_LibraryDependenciesHaveTestScope") {
      libraries := expectedCompileLibraries ++ expectedTestLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedCompileLibraries.map(library2libraryDependency(_, Some(DependencyScope.COMPILE))) ++
          expectedTestLibraries.map(library2libraryDependency(_, Some(DependencyScope.TEST)))
      })
    })
  }

  @Test
  def withoutExplicitScalaVersion_LibraryDependenciesHaveTestScope(): Unit = {
    val expectedTestLibraries = CommonLibrariesForImplicitScalaLibraryDependencyTests
    val scalaLibraries = Seq(MavenScalaLibrary(Scala_2_13_6), MavenScalaSdk(Scala_2_13_6))

    runImportingTest(new project("withoutExplicitScalaVersion_LibraryDependenciesHaveTestScope") {
      libraries := scalaLibraries ++ expectedTestLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedTestLibraries.map(library2libraryDependency(_, Some(DependencyScope.TEST))) ++
          scalaLibraries.map(library2libraryDependency)
      })
    })
  }

  @Test
  def withCompileOrder(): Unit = {
    runImportingTest(new project("withCompileOrder") {
      modules := Seq(new module("dummy-artifact-id") {
        compileOrder := CompileOrder.ScalaThenJava
      })
    })
  }

  @Test
  def resolveCompilerBridge_Scala3(): Unit = {
    runImportingTest(new project("resolveCompilerBridge_Scala3"))

    // defined in the test project `resolveCompilerBridge_Scala3/pom.xml`
    val scalaVersion = "3.4.2-RC1-bin-20240226-e0cb1e7-NIGHTLY"

    val scalaSdk = getProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk match {
      case ex: LibraryEx => ex.properties
    }

    val compilerBridge = properties.compilerBridgeBinaryJar.orNull
    assertNotNull("Scala 3 compiler bridge not configured", compilerBridge)

    org.junit.Assert.assertEquals(s"scala3-sbt-bridge-$scalaVersion.jar", compilerBridge.getFileName.toString)
  }

  @Test
  def resolveCompilerBridge_Scala2(): Unit = {
    runImportingTest(new project("resolveCompilerBridge_Scala2"))

    // defined in the test project `resolveCompilerBridge_Scala2/pom.xml`
    val scalaVersion = "2.13.13"

    val scalaSdk = getProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk match {
      case ex: LibraryEx => ex.properties
    }

    val compilerBridge = properties.compilerBridgeBinaryJar.orNull
    assertNotNull("Scala 2 compiler bridge not configured", compilerBridge)

    org.junit.Assert.assertEquals(s"scala2-sbt-bridge-$scalaVersion.jar", compilerBridge.getFileName.toString)
  }
}
