package org.jetbrains.plugins.scala.project.maven

import com.intellij.util.SystemProperties
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}
import org.jetbrains.sbt.project.ProjectStructureDsl.{ScalaSdkAttributes, libClasses, library, scalaSdkSettings}
import org.jetbrains.sbt.project.{ProjectStructureTestUtils, ScalaSdkExpectedClasspath}
import org.junit.Assert

import java.nio.file.{Files, Path}

/**
 * See also [[ProjectStructureTestUtils]]
 */
object MavenProjectStructureTestUtils {

  private lazy val mavenRepositoryRoot: String = {
    val mavenOpts = MavenUtil.getPropertiesFromMavenOpts
    //example: -Dmaven.repo.local=/mnt/cache/.m2
    val mavenRootFromMavenOpts = Option(mavenOpts.get("maven.repo.local"))

    val mavenRoot = mavenRootFromMavenOpts.getOrElse {
      mavenHomeDirectoryFromUserHome
    }.stripSuffix("/").stripSuffix("\\")

    val repositoryRoot = mavenRoot.replace("\\", "/").stripSuffix("/repository") ++ "/repository"
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

    val userHomeDir = Path.of(userHome)
    Assert.assertTrue("user home dir doesn't exist", Files.exists(userHomeDir))

    userHomeDir.resolve(".m2").toAbsolutePath.toString
  }

  private def mavenLocalArtifact(relativePath: String): String =
    s"$mavenRepositoryRoot/${relativePath.stripPrefix("/")}"

  val Scala_2_13_0: ScalaVersion = ScalaVersion.fromString("2.13.0").get
  val Scala_2_13_5: ScalaVersion = ScalaVersion.fromString("2.13.5").get
  val Scala_2_13_6: ScalaVersion = ScalaVersion.fromString("2.13.6").get
  val Scala_2_13_14: ScalaVersion = ScalaVersion.fromString("2.13.14").get
  val Scala_3_0_2: ScalaVersion = ScalaVersion.fromString("3.0.2").get
  val Scala_3_1_0: ScalaVersion = ScalaVersion.fromString("3.1.0").get

  private def getScalaSdkAttributes(version: ScalaVersion): ScalaSdkAttributes = {
    val classpath = ScalaSdkExpectedClasspath.Maven.getForVersion(version).classpath
    val classpathAbsolute = classpath.map(mavenLocalArtifact)
    ScalaSdkAttributes(version.languageLevel, classpathAbsolute, extraClasspath = Nil)
  }

  private def getSdkName(scalaVersion: ScalaVersion): String =
    s"Maven: scala-sdk-${scalaVersion.minor}"

  private def getScalaLibraryName(scalaVersion: ScalaVersion): String =
    s"Maven: ${DependencyManagerBase.scalaLibraryDescription(using scalaVersion)}"

  def MavenScalaLibrary(scalaVersion: ScalaVersion): library = {
    val jars = ProjectStructureTestUtils.expectedScalaLibraryJars(scalaVersion)
    val libClassesAbsolutePaths: Seq[String] = jars.libClasses.map(mavenLocalArtifact)
    new library(getScalaLibraryName(scalaVersion)) {
      libClasses := libClassesAbsolutePaths
    }
  }

  def MavenScalaSdk(scalaVersion: ScalaVersion): library = new library(getSdkName(scalaVersion)) {
    scalaSdkSettings := Some(getScalaSdkAttributes(scalaVersion))
  }
}
