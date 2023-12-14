package org.jetbrains.plugins.scala.project.maven

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.openapi.module.{ModuleTypeManager, StdModuleTypes}
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.{DependencyScope, ProjectRootManager}
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.SystemProperties
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.{RichFile, inWriteAction}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
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
        ProjectRootManager.getInstance(myProject).setProjectSdk(jdk)
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

  private def testProjectDir = {
    val testDataPath = TestUtils.getTestDataPath + "/../../integration/maven/testdata/maven/projects"
    val file = new File(testDataPath, getTestName(true))
    assert(file.exists(), s"testdata folder not found: $file")
    file
  }

  private def testProjectDirVFile: VirtualFile = {
    VirtualFileManager.getInstance().findFileByNioPath(testProjectDir.toPath)
  }

  private def runImportingTest(expected: project): Unit = {
    val pomFile = testProjectDir / "pom.xml"

    val pomVFile = VirtualFileManager.getInstance().findFileByNioPath(pomFile.toPath)
    Assert.assertNotNull("can't find 'pom.xml' file", pomVFile)

    importProject(pomVFile)
    assertProjectsEqual(expected, myProject)
  }

  private def scalaLibrary_3_0_0: library =
    new library(s"Maven: org.scala-lang:scala3-library_3:3.0.0") {
      libClasses := Seq("org/scala-lang/scala3-library_3/3.0.0/scala3-library_3-3.0.0.jar").map(mavenLocalArtifact)
      scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_3_0, Seq(
        "com/google/protobuf/protobuf-java/3.7.0/protobuf-java-3.7.0.jar",
        "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
        "org/jline/jline-reader/3.19.0/jline-reader-3.19.0.jar",
        "org/jline/jline-terminal-jna/3.19.0/jline-terminal-jna-3.19.0.jar",
        "org/jline/jline-terminal/3.19.0/jline-terminal-3.19.0.jar",
        "org/scala-lang/modules/scala-asm/9.1.0-scala-1/scala-asm-9.1.0-scala-1.jar",
        "org/scala-lang/scala-library/2.13.5/scala-library-2.13.5.jar",
        "org/scala-lang/scala3-compiler_3/3.0.0/scala3-compiler_3-3.0.0.jar",
        "org/scala-lang/scala3-interfaces/3.0.0/scala3-interfaces-3.0.0.jar",
        "org/scala-lang/scala3-library_3/3.0.0/scala3-library_3-3.0.0.jar",
        "org/scala-lang/tasty-core_3/3.0.0/tasty-core_3-3.0.0.jar",
        "org/scala-sbt/compiler-interface/1.3.5/compiler-interface-1.3.5.jar",
        "org/scala-sbt/util-interface/1.3.0/util-interface-1.3.0.jar"
      ).map(mavenLocalArtifact), extraClasspath = Nil))
    }

  private lazy val mavenRepositoryRoot: String = {
    val mavenOpts = MavenUtil.getPropertiesFromMavenOpts
    //example: -Dmaven.repo.local=/mnt/cache/.m2
    val mavenRootFromMavenOpts = Option(mavenOpts.get("maven.repo.local"))

    val mavenRoot = mavenRootFromMavenOpts.getOrElse {
      //NOTE: if this doesn't work for some reason, also consider using
      //org.jetbrains.idea.maven.utils.MavenUtil.resolveMavenHomeDirectory (it doesn't respect MAVEN_OPTS though)
      val userHome = SystemProperties.getUserHome
      Assert.assertNotNull("user.home property is not set", userHome)

      val userHomeDir = new File(userHome)
      Assert.assertTrue("user home dir doesn't exist", userHomeDir.exists())

      (userHomeDir / ".m2").getAbsolutePath
    }.stripSuffix("/").stripSuffix("\\")

    (mavenRoot + "/repository").replace("\\", "/")
  }

  private def mavenLocalArtifact(relativePath: String): String = {
    mavenRepositoryRoot + "/" + relativePath.stripPrefix("/")
  }

  private def runImportingTest_Common(
    expectedProjectName: String,
    expectedModuleName: String,
    expectedLibraries: Seq[library]
  ): Unit =
    runImportingTest(new project(expectedProjectName) {
      libraries := expectedLibraries
      modules := Seq(new module(expectedModuleName) {
        contentRoots := Seq(testProjectDirVFile.toNioPath.toAbsolutePath.toString)
        sources := Seq("src/main/scala", "src/main/java")
        testSources := Seq("src/test/scala", "src/test/java")
        resources := Seq("src/main/resources")
        testResources := Seq("src/test/resources")
        excluded := Seq("target")
        libraryDependencies := expectedLibraries.map(library2libraryDependency)
        compileOrder := CompileOrder.Mixed
      })
    })

  def testWithScala2(): Unit =
    runImportingTest_Common("testWithScala2", "projectWithScala2", Seq(
      new library(s"Maven: org.scala-lang:scala-library:2.13.6") {
        libClasses := Seq(
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar"
        ).map(mavenLocalArtifact)
        scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_2_13, Seq(
          "org/scala-lang/scala-compiler/2.13.6/scala-compiler-2.13.6.jar",
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar",
          "org/scala-lang/scala-reflect/2.13.6/scala-reflect-2.13.6.jar",
          "org/jline/jline/3.19.0/jline-3.19.0.jar",
          "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
        ).map(mavenLocalArtifact), extraClasspath = Nil))
      }
    ))

  def testWithScala3_0(): Unit =
    runImportingTest_Common("testWithScala3_0", "projectWithScala3_0", Seq(
      new library(s"Maven: org.scala-lang:scala-library:2.13.5") {
        libClasses := Seq("org/scala-lang/scala-library/2.13.5/scala-library-2.13.5.jar").map(mavenLocalArtifact)
        scalaSdkSettings := None
      },
      scalaLibrary_3_0_0
    ))

  def testWithScala3_1(): Unit =
    runImportingTest_Common("testWithScala3_1", "projectWithScala3_1", Seq(
      new library(s"Maven: org.scala-lang:scala-library:2.13.6") {
        libClasses := Seq("org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar").map(mavenLocalArtifact)
        scalaSdkSettings := None
      },
      new library(s"Maven: org.scala-lang:scala3-library_3:3.1.0") {
        libClasses := Seq("org/scala-lang/scala3-library_3/3.1.0/scala3-library_3-3.1.0.jar").map(mavenLocalArtifact)
        scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_3_1, Seq(
          "com/google/protobuf/protobuf-java/3.7.0/protobuf-java-3.7.0.jar",
          "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
          "org/jline/jline-reader/3.19.0/jline-reader-3.19.0.jar",
          "org/jline/jline-terminal-jna/3.19.0/jline-terminal-jna-3.19.0.jar",
          "org/jline/jline-terminal/3.19.0/jline-terminal-3.19.0.jar",
          "org/scala-lang/modules/scala-asm/9.1.0-scala-1/scala-asm-9.1.0-scala-1.jar",
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar",
          "org/scala-lang/scala3-compiler_3/3.1.0/scala3-compiler_3-3.1.0.jar",
          "org/scala-lang/scala3-interfaces/3.1.0/scala3-interfaces-3.1.0.jar",
          "org/scala-lang/scala3-library_3/3.1.0/scala3-library_3-3.1.0.jar",
          "org/scala-lang/tasty-core_3/3.1.0/tasty-core_3-3.1.0.jar",
          "org/scala-sbt/compiler-interface/1.3.5/compiler-interface-1.3.5.jar",
          "org/scala-sbt/util-interface/1.3.0/util-interface-1.3.0.jar"
        ).map(mavenLocalArtifact), extraClasspath = Nil))
      }
    ))

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
      new library(s"Maven: org.scala-lang:scala-library:2.13.6") {
        libClasses := Seq(
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar"
        ).map(mavenLocalArtifact)
        scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_2_13, Seq(
          "org/scala-lang/scala-compiler/2.13.8/scala-compiler-2.13.8.jar",
          "org/scala-lang/scala-library/2.13.8/scala-library-2.13.8.jar",
          "org/scala-lang/scala-reflect/2.13.8/scala-reflect-2.13.8.jar",
          "org/jline/jline/3.21.0/jline-3.21.0.jar",
          "net/java/dev/jna/jna/5.9.0/jna-5.9.0.jar",
        ).map(mavenLocalArtifact), extraClasspath = Nil))
      }
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
      new library(s"Maven: org.scala-lang:scala-library:2.13.6") {
        libClasses := Seq(
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar"
        ).map(mavenLocalArtifact)
        scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_2_13, Seq(
          "org/scala-lang/scala-compiler/2.13.5/scala-compiler-2.13.5.jar",
          "org/scala-lang/scala-library/2.13.5/scala-library-2.13.5.jar",
          "org/scala-lang/scala-reflect/2.13.5/scala-reflect-2.13.5.jar",
          "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
          "org/jline/jline/3.19.0/jline-3.19.0.jar",
        ).map(mavenLocalArtifact), extraClasspath = Nil))
      }
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
      new library(s"Maven: org.scala-lang:scala-library:2.13.6") {
        libClasses := Seq(
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar"
        ).map(mavenLocalArtifact)
        scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_2_13, Seq(
          "org/scala-lang/scala-compiler/2.13.0/scala-compiler-2.13.0.jar",
          "org/scala-lang/scala-library/2.13.0/scala-library-2.13.0.jar",
          "org/scala-lang/scala-reflect/2.13.0/scala-reflect-2.13.0.jar",
          "jline/jline/2.14.6/jline-2.14.6.jar",
        ).map(mavenLocalArtifact), extraClasspath = Nil))
      }
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
      new library(s"Maven: org.scala-lang:scala-library:2.13.0") {
        libClasses := Seq(
          "org/scala-lang/scala-library/2.13.0/scala-library-2.13.0.jar"
        ).map(mavenLocalArtifact)
        scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_2_13, Seq(
          "org/scala-lang/scala-compiler/2.13.0/scala-compiler-2.13.0.jar",
          "org/scala-lang/scala-library/2.13.0/scala-library-2.13.0.jar",
          "org/scala-lang/scala-reflect/2.13.0/scala-reflect-2.13.0.jar",
          "jline/jline/2.14.6/jline-2.14.6.jar",
        ).map(mavenLocalArtifact), extraClasspath = Nil))
      }
    )
    val expectedTestLibraries = Seq(
      new library(s"Maven: org.scala-lang:scala-library:2.13.6") {
        libClasses := Seq(
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar"
        ).map(mavenLocalArtifact)
        scalaSdkSettings := None
      }
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
      new library(s"Maven: org.scala-lang:scala-library:2.13.6") {
        libClasses := Seq(
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar"
        ).map(mavenLocalArtifact)
        scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_2_13, Seq(
          "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
          "org/jline/jline/3.19.0/jline-3.19.0.jar",
          "org/scala-lang/scala-compiler/2.13.6/scala-compiler-2.13.6.jar",
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar",
          "org/scala-lang/scala-reflect/2.13.6/scala-reflect-2.13.6.jar",
        ).map(mavenLocalArtifact), extraClasspath = Nil))
      }
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
