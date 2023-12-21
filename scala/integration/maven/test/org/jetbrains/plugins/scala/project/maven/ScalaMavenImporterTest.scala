package org.jetbrains.plugins.scala.project.maven

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.openapi.module.{ModuleTypeManager, StdModuleTypes}
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.{DependencyScope, ProjectRootManager}
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.{RichFile, inWriteAction}
import org.jetbrains.plugins.scala.project.maven.MavenProjectStructureTestUtils._
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.{ExactMatch, ProjectStructureMatcher}
import org.junit.Assert
import org.junit.experimental.categories.Category

import java.io.File

//noinspection ApiStatus
@Category(Array(classOf[SlowTests]))
abstract class ScalaMavenImporterTest
  extends MavenImportingTestCase
    with ProjectStructureMatcher
    with ExactMatch {

  import ProjectStructureMatcher.ProjectComparisonOptions.Implicit.default

  /** None means use whatever default JDK is chosen by IDEA (most probably internal IDEA JDK) */
  protected def projectJdkVersion: Option[LanguageLevel]

  private var jdk: Sdk = _

  override protected def setUp(): Unit = {
    super.setUp()

    // Without this HACK for some reason different instances of com.intellij.openapi.module.JavaModuleType will be used
    // in org.jetbrains.idea.maven.importing.MavenImporter (e.g. ScalaMavenImporter)
    // and org.jetbrains.idea.maven.importing.MavenModuleImporter
    // (Note that it uses `==` instead of `equals` for some reason: `importer.getModuleType() == moduleType`)
    ModuleTypeManager.getInstance.registerModuleType(StdModuleTypes.JAVA)

    projectJdkVersion.foreach { jdkVersion =>
      inWriteAction {
        jdk = SmartJDKLoader.getOrCreateJDK(jdkVersion)
        ProjectRootManager.getInstance(getProject).setProjectSdk(jdk)
      }
    }
  }

  override protected def tearDown(): Unit = {
    if (jdk != null) {
      val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
      inWriteAction(jdkTable.removeJdk(jdk))
    }

    ModuleTypeManager.getInstance.unregisterModuleType(StdModuleTypes.JAVA)

    super.tearDown()
  }

  private def getTestProjectDir: File = {
    val testDataPath = TestUtils.getTestDataPath + "/../../integration/maven/testdata/maven/projects"
    val file = new File(testDataPath, getTestName(true))
    assert(file.exists(), s"testdata folder not found: $file")
    file
  }

  private def getTestProjectDirVFile: VirtualFile =
    VirtualFileManager.getInstance().findFileByNioPath(getTestProjectDir.toPath)

  private def runImportingTest(expected: project): Unit = {
    val pomFile = getTestProjectDir / "pom.xml"

    val pomVFile = VirtualFileManager.getInstance().findFileByNioPath(pomFile.toPath)
    Assert.assertNotNull("can't find 'pom.xml' file", pomVFile)

    importProject(pomVFile)
    assertProjectsEqual(expected, getProject)
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
        contentRoots := Seq(getTestProjectDirVFile.toNioPath.toAbsolutePath.toString)
        sources := expectedSourceDirectories
        testSources := expectedTestSourceDirectories
        resources := Seq("src/main/resources")
        testResources := Seq("src/test/resources")
        excluded := Seq("target")
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
        compileOrder := CompileOrder.Mixed
      })
    })

  def testWithScala2(): Unit =
    runImportingTest_Common(
      "testWithScala2",
      "projectWithScala2",
      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(MavenScalaLibrary(Scala_2_13_6, isSdk = true))
    )

  def testWithScala2_WithExplicitSourceDirectoriesSet(): Unit =
    runImportingTest_Common(
      "testWithScala2_WithExplicitSourceDirectoriesSet",
      "projectWithScala2",
      //When Maven build has explicit source dirs (sourceDirectory, testSourceDirectory),
      //default java source dirs are replaced
      Seq("src/main/scala"),
      Seq("src/test/scala"),
      Seq(MavenScalaLibrary(Scala_2_13_6, isSdk = true))
    )

  def testWithScala2_WithoutScalaMavenPlugin(): Unit =
    runImportingTest_Common(
      "testWithScala2_WithoutScalaMavenPlugin",
      "projectWithScala2",
      Seq("src/main/java"),
      Seq("src/test/java"),
      Seq(MavenScalaLibrary(Scala_2_13_6, isSdk = false))
    )

  def testWithScala3_0(): Unit = {
    runImportingTest_Common(
      "testWithScala3_0",
      "projectWithScala3_0",

      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(
        MavenScalaLibrary(Scala_2_13_5, isSdk = false),
        MavenScalaLibrary(Scala_3_0_0, isSdk = true)
      )
    )
  }

  def testWithScala3_1(): Unit = {
    runImportingTest_Common(
      "testWithScala3_1",
      "projectWithScala3_1",
      Seq("src/main/scala", "src/main/java"),
      Seq("src/test/scala", "src/test/java"),
      Seq(
        MavenScalaLibrary(Scala_2_13_6, isSdk = false),
        MavenScalaLibrary(Scala_3_1_0, isSdk = true)
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

  def testWithImplicitScalaLibraryDependency_compilerVersionLargest(): Unit = {
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6, scalaSdkVersion = Scala_2_13_8)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("testWithImplicitScalaLibraryDependency_compilerVersionLargest") {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })
  }

  def testWithImplicitScalaLibraryDependency_compilerVersionInTheMiddle(): Unit = {
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6, scalaSdkVersion = Scala_2_13_5)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("testWithImplicitScalaLibraryDependency_compilerVersionInTheMiddle") {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })
  }

  def testWithImplicitScalaLibraryDependency_compilerVersionSmallest(): Unit = {
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6, scalaSdkVersion = Scala_2_13_0)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("testWithImplicitScalaLibraryDependency_compilerVersionSmallest") {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
      })
    })
  }

  def testWithImplicitScalaLibraryDependency_compilerVersionSmallest_LibraryDependenciesHaveTestScope(): Unit = {
    val expectedCompileLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_0, isSdk = true)
    )

    val expectedTestLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6, isSdk = false)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("testWithImplicitScalaLibraryDependency_compilerVersionSmallest_LibraryDependenciesHaveTestScope") {
      libraries := expectedCompileLibraries ++ expectedTestLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedCompileLibraries.map(library2libraryDependency(_, Some(DependencyScope.COMPILE))) ++
          expectedTestLibraries.map(library2libraryDependency(_, Some(DependencyScope.TEST)))
      })
    })
  }

  def testWithoutExplicitScalaVersion_LibraryDependenciesHaveTestScope(): Unit = {
    val expectedLibraries = Seq(
      MavenScalaLibrary(Scala_2_13_6, isSdk = true)
    ) ++ CommonLibrariesForImplicitScalaLibraryDependencyTests

    runImportingTest(new project("testWithoutExplicitScalaVersion_LibraryDependenciesHaveTestScope") {
      libraries := expectedLibraries
      modules := Seq(new module("dummy-artifact-id") {
        libraryDependencies := expectedLibraries.map(library2libraryDependency(_, Some(DependencyScope.TEST)))
      })
    })
  }

  def testWithCompileOrder(): Unit = {
    runImportingTest(new project("testWithCompileOrder") {
      modules := Seq(new module("dummy-artifact-id") {
        compileOrder := CompileOrder.ScalaThenJava
      })
    })
  }
}
