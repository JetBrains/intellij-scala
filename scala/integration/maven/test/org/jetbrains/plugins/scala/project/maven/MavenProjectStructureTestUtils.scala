package org.jetbrains.plugins.scala.project.maven

import com.intellij.util.SystemProperties
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}
import org.jetbrains.sbt.project.ProjectStructureDsl.{ScalaSdkAttributes, libClasses, library, scalaSdkSettings}
import org.junit.Assert

import java.io.File

/**
 * See also [[org.jetbrains.sbt.project.ProjectStructureTestUtils]]
 */
object MavenProjectStructureTestUtils {

  private lazy val mavenRepositoryRoot: String = {
    val mavenOpts = MavenUtil.getPropertiesFromMavenOpts
    //example: -Dmaven.repo.local=/mnt/cache/.m2
    val mavenRootFromMavenOpts = Option(mavenOpts.get("maven.repo.local"))

    val mavenRoot = mavenRootFromMavenOpts.getOrElse {
      mavenHomeDirectoryFromUserHome
    }.stripSuffix("/").stripSuffix("\\")

    val repositoryRoot = (mavenRoot + "/repository").replace("\\", "/")
    println(
      s"""### Detected maven repository root: $repositoryRoot
         |### mavenRootFromMavenOpts: $mavenRootFromMavenOpts
         |""".stripMargin.trim
    )
    repositoryRoot
  }

  //NOTE: if this doesn't work for some reason, also consider using
  //org.jetbrains.idea.maven.utils.MavenUtil.resolveMavenHomeDirectory (it doesn't respect MAVEN_OPTS though)
  private def mavenHomeDirectoryFromUserHome: String = {
    val userHome = SystemProperties.getUserHome
    Assert.assertNotNull("user.home property is not set", userHome)

    val userHomeDir = new File(userHome)
    Assert.assertTrue("user home dir doesn't exist", userHomeDir.exists())

    (userHomeDir / ".m2").getAbsolutePath
  }

  private def mavenLocalArtifact(relativePath: String): String =
    s"$mavenRepositoryRoot/${relativePath.stripPrefix("/")}"

  private val ScalaSdkClasspath_2_13_0: Seq[String] = Seq(
    "org/scala-lang/scala-compiler/2.13.0/scala-compiler-2.13.0.jar",
    "org/scala-lang/scala-library/2.13.0/scala-library-2.13.0.jar",
    "org/scala-lang/scala-reflect/2.13.0/scala-reflect-2.13.0.jar",
    "jline/jline/2.14.6/jline-2.14.6.jar",
  ).map(mavenLocalArtifact)

  private val ScalaSdkClasspath_2_13_5: Seq[String] = Seq(
    "org/scala-lang/scala-compiler/2.13.5/scala-compiler-2.13.5.jar",
    "org/scala-lang/scala-library/2.13.5/scala-library-2.13.5.jar",
    "org/scala-lang/scala-reflect/2.13.5/scala-reflect-2.13.5.jar",
    "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
    "org/jline/jline/3.19.0/jline-3.19.0.jar",
  ).map(mavenLocalArtifact)

  private val ScalaSdkClasspath_2_13_6: Seq[String] = Seq(
    "org/scala-lang/scala-compiler/2.13.6/scala-compiler-2.13.6.jar",
    "org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar",
    "org/scala-lang/scala-reflect/2.13.6/scala-reflect-2.13.6.jar",
    "org/jline/jline/3.19.0/jline-3.19.0.jar",
    "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
  ).map(mavenLocalArtifact)

  private val ScalaSdkClasspath_2_13_8: Seq[String] = Seq(
    "org/scala-lang/scala-compiler/2.13.8/scala-compiler-2.13.8.jar",
    "org/scala-lang/scala-library/2.13.8/scala-library-2.13.8.jar",
    "org/scala-lang/scala-reflect/2.13.8/scala-reflect-2.13.8.jar",
    "net/java/dev/jna/jna/5.9.0/jna-5.9.0.jar",
    "org/jline/jline/3.21.0/jline-3.21.0.jar",
  ).map(mavenLocalArtifact)

  private val ScalaSdkClasspath_3_0_0: Seq[String] = Seq(
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
  ).map(mavenLocalArtifact)

  private val ScalaSdkClasspath_3_1_0: Seq[String] = Seq(
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
  ).map(mavenLocalArtifact)

  val Scala_2_13_0: ScalaVersion = ScalaVersion.fromString("2.13.0").get
  val Scala_2_13_5: ScalaVersion = ScalaVersion.fromString("2.13.5").get
  val Scala_2_13_6: ScalaVersion = ScalaVersion.fromString("2.13.6").get
  val Scala_2_13_8: ScalaVersion = ScalaVersion.fromString("2.13.8").get
  val Scala_3_0_0: ScalaVersion = ScalaVersion.fromString("3.0.0").get
  val Scala_3_1_0: ScalaVersion = ScalaVersion.fromString("3.1.0").get

  def getScalaSdkClasspath(version: ScalaVersion): Seq[String] = version match {
    case Scala_2_13_0 => ScalaSdkClasspath_2_13_0
    case Scala_2_13_5 => ScalaSdkClasspath_2_13_5
    case Scala_2_13_6 => ScalaSdkClasspath_2_13_6
    case Scala_2_13_8 => ScalaSdkClasspath_2_13_8
    case Scala_3_0_0 => ScalaSdkClasspath_3_0_0
    case Scala_3_1_0 => ScalaSdkClasspath_3_1_0
    case _ =>
      throw new RuntimeException(s"Compiler classpath for version ${version.minor} is not specified yet")
  }

  private def getScalaSdkAttributes(version: ScalaVersion): ScalaSdkAttributes = {
    val classpath = getScalaSdkClasspath(version)
    ScalaSdkAttributes(version.languageLevel, classpath, extraClasspath = Nil)
  }

  private def getScalaLibraryName(scalaVersion: ScalaVersion): String =
    s"Maven: ${DependencyManagerBase.scalaLibraryDescription(scalaVersion)}"

  private def getScalaLibraryPath(version: ScalaVersion): String = {
    val versionStr = version.minor
    if (version.isScala2)
      mavenLocalArtifact(s"org/scala-lang/scala-library/$versionStr/scala-library-$versionStr.jar")
    else
      mavenLocalArtifact(s"org/scala-lang/scala3-library_3/$versionStr/scala3-library_3-$versionStr.jar")
  }

  def MavenScalaLibrary(scalaVersion: ScalaVersion, isSdk: Boolean): library = new library(getScalaLibraryName(scalaVersion)) {
    libClasses := Seq(getScalaLibraryPath(scalaVersion))
    scalaSdkSettings := Option.when(isSdk)(getScalaSdkAttributes(scalaVersion))
  }

  def MavenScalaLibrary(
    scalaLibraryVersion: ScalaVersion,
    scalaSdkVersion: ScalaVersion
  ): library = new library(getScalaLibraryName(scalaLibraryVersion)) {
    libClasses := Seq(getScalaLibraryPath(scalaLibraryVersion))
    scalaSdkSettings := Some(getScalaSdkAttributes(scalaSdkVersion))
  }
}
