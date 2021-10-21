package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import org.jetbrains.idea.maven.MavenImportingTestCase
import org.jetbrains.plugins.scala.DependencyManagerBase.scalaLibraryDescription
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.project.maven.ScalaMavenImporter.RichFile
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{ScalaVersion, SlowTests}
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.{ExactMatch, ProjectStructureMatcher}
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import java.io.File

@Category(Array(classOf[SlowTests]))
class ScalaMavenImporterTest
  extends MavenImportingTestCase
    with ProjectStructureMatcher
    with ExactMatch {

  private def testProjectDir: File = {
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
    assertNotNull("can't find 'pom.xml' file", pomVFile)

    importProject(pomVFile)
    assertProjectsEqual(expected, myProject)
  }

  private lazy val mavenRepositoryRoot: String = {
    val userHome = System.getProperty("user.home")
    assertNotNull("user.home property is not set", userHome)

    val userHomeDir = new File(userHome)
    assertTrue("user home dir doesn't exist", userHomeDir.exists())

    (userHomeDir / ".m2" / "repository").getAbsolutePath.replace("\\", "/").stripSuffix("/")
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
      })
    })


  def testWithScala2(): Unit =
    runImportingTest_Common("testWithScala2", "projectWithScala2", Seq(
      new library(s"Maven: ${scalaLibraryDescription(ScalaVersion.fromString("2.13.6").get)}") {
        classes := Seq(
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar"
        ).map(mavenLocalArtifact)
        scalaSdkSettings := Some(ScalaSdkAttributes(ScalaLanguageLevel.Scala_2_13, Seq(
          "org/scala-lang/scala-compiler/2.13.6/scala-compiler-2.13.6.jar",
          "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar",
          "org/scala-lang/scala-reflect/2.13.6/scala-reflect-2.13.6.jar",
          "org/jline/jline/3.19.0/jline-3.19.0.jar",
          "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
        ).map(mavenLocalArtifact)))
      }
    ))

  def testWithScala3_0(): Unit =
    runImportingTest_Common("testWithScala3_0", "projectWithScala3_0", Seq(
      new library(s"Maven: ${scalaLibraryDescription(ScalaVersion.fromString("2.13.5").get)}") {
        classes := Seq("org/scala-lang/scala-library/2.13.5/scala-library-2.13.5.jar").map(mavenLocalArtifact)
        scalaSdkSettings := None
      },
      new library(s"Maven: ${scalaLibraryDescription(ScalaVersion.fromString("3.0.0").get)}") {
        classes := Seq("org/scala-lang/scala3-library_3/3.0.0/scala3-library_3-3.0.0.jar").map(mavenLocalArtifact)
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
        ).map(mavenLocalArtifact)))
      }
    ))

  def testWithScala3_1(): Unit =
    runImportingTest_Common("testWithScala3_1", "projectWithScala3_1", Seq(
      new library(s"Maven: ${scalaLibraryDescription(ScalaVersion.fromString("2.13.6").get)}") {
        classes := Seq("org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar").map(mavenLocalArtifact)
        scalaSdkSettings := None
      },
      new library(s"Maven: ${scalaLibraryDescription(ScalaVersion.fromString("3.1.0").get)}") {
        classes := Seq("org/scala-lang/scala3-library_3/3.1.0/scala3-library_3-3.1.0.jar").map(mavenLocalArtifact)
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
        ).map(mavenLocalArtifact)))
      }
    ))
}
