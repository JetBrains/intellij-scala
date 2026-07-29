package org.jetbrains.plugins.scala.project.maven

import com.intellij.maven.testFramework.fixtures.{MavenFixturesKt, MavenImportingTestFixture, MavenTestFixtureImportKt}
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.CoroutinesKt
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.{DependencyScope, ProjectRootManager}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.maven.MavenProjectStructureTestUtils.*
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureAssertionsFixture
import org.jetbrains.sbt.project.ProjectStructureDsl.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull}
import org.junit.jupiter.api.{Test, TestInfo}

import java.nio.file.{Files, Path}
import java.util.Collections
import kotlin.coroutines.Continuation

/**
 * @param projectJdkVersion the JDK version to set up as the project JDK around each test;
 *                          None means use the default JDK set up by the Maven test fixture ("Maven Test JDK")
 */
@TestApplication
abstract class ScalaMavenImporterTest(projectJdkVersion: Option[LanguageLevel]):

  /**
   * The fixture must be defined as a class member in order to be initialized (per test method, before the test)
   * and torn down (after the test) by the `TestFixtureExtension` installed via `@TestApplication`.
   *
   * Note: the fixture project is always named "project" (it is created in a fresh temp directory), not after the
   * test method. Expected project names in assertions use [[getProject]]`.getName`.
   */
  private val mavenFixture: TestFixture[MavenImportingTestFixture] =
    MavenFixturesKt.mavenImportingFixture(
      "bundled",                          // mavenVersion
      MavenConstants.MODEL_VERSION_4_0_0, // modelVersion
      true,                               // skipPluginResolution
      null                                // initialPom: each test imports its own pom from testdata
    )

  protected def getProject: Project = mavenFixture.get().getProject

  /**
   * Sets up the project JDK for `projectJdkVersion` around `test` and removes it afterwards.
   * SmartJDKLoader registers the JDK in the application-level table without a disposable,
   * so it must be removed manually.
   */
  protected def withProjectJdk(test: => Unit): Unit =
    projectJdkVersion match
      case Some(jdkVersion) =>
        val jdk = WriteAction.computeAndWait: () =>
          val jdk = SmartJDKLoader.getOrCreateJDK(jdkVersion)
          ProjectRootManager.getInstance(getProject).setProjectSdk(jdk)
          jdk

        try test
        finally WriteAction.runAndWait(() => JavaAwareProjectJdkTableImpl.getInstanceEx.removeJdk(jdk))
      case None =>
        test

  private def getTestProjectDir(using testInfo: TestInfo): Path =
    //Replacement for the JUnit 3 getTestName(true): no test method name here starts with "test",
    //so the old value was the method name verbatim. It maps 1:1 to the testdata directory names.
    val testMethodName = testInfo.getTestMethod.get().getName
    val testDataPath = Path.of(TestUtils.getTestDataPath)
      .getParent.getParent
      .resolve("integration").resolve("maven").resolve("testdata").resolve("maven").resolve("projects")
      .resolve(testMethodName)
    assert(Files.exists(testDataPath), s"testdata directory not found: $testDataPath")
    testDataPath

  private def getTestProjectDirVFile(using TestInfo): VirtualFile =
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(getTestProjectDir)

  /** Bridges a single Kotlin `suspend` function call into blocking code. */
  private def runBlockingUnit(body: Continuation[? >: kotlin.Unit] => AnyRef): Unit =
    CoroutinesKt.runBlockingMaybeCancellable[kotlin.Unit]((_, cont) => body(cont))

  protected def runImportingTest(expected: project)(using TestInfo): Unit =
    val pomFile = getTestProjectDir.resolve("pom.xml")

    val pomVFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pomFile)
    assertNotNull(pomVFile, "can't find 'pom.xml' file")

    val fixture = mavenFixture.get()

    //1:1 port of the deprecated MavenImportingTestCase.importProjects: doImportProjectsAsync sets the original
    //files and explicit profiles, runs an incremental sync and asserts that there are no reading errors...
    runBlockingUnit: cont =>
      MavenTestFixtureImportKt.doImportProjectsAsync(
        fixture,
        Collections.singletonList(pomVFile),
        true,                // failOnReadingError
        Array.empty[String], // profiles
        cont
      )

    //...unlike the old importProjects, it does not wait for indexes and project configuration, so do it here
    IndexingTestUtil.waitUntilIndexesAreReady(getProject)
    runBlockingUnit(MavenTestFixtureImportKt.awaitConfiguration(fixture, _))

    ProjectStructureAssertionsFixture(getProject).assertProjectsEqual(expected)
  end runImportingTest

  private def runImportingTest_Common(
    expectedModuleName: String,
    expectedSourceDirectories: Seq[String],
    expectedTestSourceDirectories: Seq[String],
    expectedLibraries: Seq[library],
  )(using TestInfo): Unit =
    runImportingTest(new project(getProject.getName) {
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
  def withScala2(using TestInfo): Unit = withProjectJdk:
    runImportingTest_Common(
      "projectWithScala2",
      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(MavenScalaLibrary(Scala_2_13_6), MavenScalaSdk(Scala_2_13_6))
    )

  @Test
  def withTwoModulesWithScala2And3(using TestInfo): Unit = withProjectJdk:
    runImportingTest(new project(getProject.getName) {
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

  @Test
  def withScala2_WithExplicitSourceDirectoriesSet(using TestInfo): Unit = withProjectJdk:
    runImportingTest_Common(
      "projectWithScala2",
      //When Maven build has explicit source dirs (sourceDirectory, testSourceDirectory),
      //default java source dirs are replaced
      Seq("src/main/scala"),
      Seq("src/test/scala"),
      Seq(MavenScalaLibrary(Scala_2_13_6), MavenScalaSdk(Scala_2_13_6))
    )

  @Test
  def withScala2_WithoutScalaMavenPlugin(using TestInfo): Unit = withProjectJdk:
    runImportingTest_Common(
      "projectWithScala2",
      Seq("src/main/java"),
      Seq("src/test/java"),
      Seq(MavenScalaLibrary(Scala_2_13_6))
    )

  @Test
  def withScala3_0(using TestInfo): Unit = withProjectJdk:
    runImportingTest_Common(
      "projectWithScala3_0",
      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(
        MavenScalaLibrary(Scala_2_13_6),
        MavenScalaLibrary(Scala_3_0_2),
        MavenScalaSdk(Scala_3_0_2)
      )
    )

  @Test
  def withScala3_1(using TestInfo): Unit = withProjectJdk:
    runImportingTest_Common(
      "projectWithScala3_1",
      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(
        MavenScalaLibrary(Scala_2_13_6),
        MavenScalaLibrary(Scala_3_1_0),
        MavenScalaSdk(Scala_3_1_0),
      )
    )

  private val CommonLibrariesForImplicitScalaLibraryDependencyTests = Seq(
    library("Maven: junit:junit:4.13.1"),
    library("Maven: org.hamcrest:hamcrest-core:1.3"),
    library("Maven: org.scala-lang.modules:scala-xml_2.13:2.0.1"),
    library("Maven: org.scala-lang:scala-reflect:2.13.6"),
    library("Maven: org.scala-sbt:test-interface:1.0"),
    library("Maven: org.scalactic:scalactic_2.13:3.2.11"),
    library("Maven: org.scalameta:junit-interface:0.7.25"),
    library("Maven: org.scalameta:munit_2.13:0.7.25"),
    library("Maven: org.scalatest:scalatest-compatible:3.2.11"),
    library("Maven: org.scalatest:scalatest-core_2.13:3.2.11"),
  )

  @Test
  def withImplicitScalaLibraryDependency_compilerVersionLargest(using TestInfo): Unit = withProjectJdk:
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6),
      MavenScalaSdk(Scala_2_13_14)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project(getProject.getName) {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })

  @Test
  def withImplicitScalaLibraryDependency_compilerVersionInTheMiddle(using TestInfo): Unit = withProjectJdk:
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6),
      MavenScalaSdk(Scala_2_13_5)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project(getProject.getName) {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })

  @Test
  def withImplicitScalaLibraryDependency_compilerVersionSmallest(using TestInfo): Unit = withProjectJdk:
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6),
      MavenScalaSdk(Scala_2_13_0)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project(getProject.getName) {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })

  @Test
  def withImplicitScalaLibraryDependency_compilerVersionSmallest_LibraryDependenciesHaveTestScope(using TestInfo): Unit = withProjectJdk:
    val expectedCompileLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_0),
      MavenScalaSdk(Scala_2_13_0)
    )

    val expectedTestLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project(getProject.getName) {
      libraries := expectedCompileLibraries ++ expectedTestLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedCompileLibraries.map(library2libraryDependency(_, Some(DependencyScope.COMPILE))) ++
          expectedTestLibraries.map(library2libraryDependency(_, Some(DependencyScope.TEST)))
      })
    })

  @Test
  def withoutExplicitScalaVersion_LibraryDependenciesHaveTestScope(using TestInfo): Unit = withProjectJdk:
    val expectedTestLibraries = CommonLibrariesForImplicitScalaLibraryDependencyTests
    val scalaLibraries = Seq(MavenScalaLibrary(Scala_2_13_6), MavenScalaSdk(Scala_2_13_6))

    runImportingTest(new project(getProject.getName) {
      libraries := scalaLibraries ++ expectedTestLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedTestLibraries.map(library2libraryDependency(_, Some(DependencyScope.TEST))) ++
          scalaLibraries.map(library2libraryDependency)
      })
    })

  @Test
  def withCompileOrder(using TestInfo): Unit = withProjectJdk:
    runImportingTest(new project(getProject.getName) {
      modules := Seq(new module("dummy-artifact-id") {
        compileOrder := CompileOrder.ScalaThenJava
      })
    })

  @Test
  def resolveCompilerBridge_Scala3(using TestInfo): Unit = withProjectJdk:
    runImportingTest(project(getProject.getName))

    // defined in the test project `resolveCompilerBridge_Scala3/pom.xml`
    val scalaVersion = "3.4.2-RC1-bin-20240226-e0cb1e7-NIGHTLY"

    val scalaSdk = getProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull(scalaSdk, "Scala SDK not configured")

    val properties = scalaSdk match
      case ex: LibraryEx => ex.properties

    val compilerBridge = properties.compilerBridgeBinaryJar.orNull
    assertNotNull(compilerBridge, "Scala 3 compiler bridge not configured")

    assertEquals(s"scala3-sbt-bridge-$scalaVersion.jar", compilerBridge.getFileName.toString)

  @Test
  def resolveCompilerBridge_Scala2(using TestInfo): Unit = withProjectJdk:
    runImportingTest(project(getProject.getName))

    // defined in the test project `resolveCompilerBridge_Scala2/pom.xml`
    val scalaVersion = "2.13.13"

    val scalaSdk = getProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull(scalaSdk, "Scala SDK not configured")

    val properties = scalaSdk match
      case ex: LibraryEx => ex.properties

    val compilerBridge = properties.compilerBridgeBinaryJar.orNull
    assertNotNull(compilerBridge, "Scala 2 compiler bridge not configured")

    assertEquals(s"scala2-sbt-bridge-$scalaVersion.jar", compilerBridge.getFileName.toString)
end ScalaMavenImporterTest
