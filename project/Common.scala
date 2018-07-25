import sbt._
import Keys._
import org.jetbrains.sbtidea.Keys._

import scala.language.implicitConversions
import scala.language.postfixOps

object Common {
  lazy val communityFullClasspath: TaskKey[Classpath] =
    taskKey[Classpath]("scalaCommunity module's fullClasspath in Compile and Test scopes")

  lazy val testConfigDir: SettingKey[File] =
    settingKey[File]("IDEA's config directory for tests")

  lazy val testSystemDir: SettingKey[File] =
    settingKey[File]("IDEA's system directory for tests")

  def newProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      name := projectName,
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
      unmanagedSourceDirectories in Test += baseDirectory.value / "test",
      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
      unmanagedResourceDirectories in Test += baseDirectory.value / "testResources",
      libraryDependencies ++= Seq(Dependencies.junitInterface),
      updateOptions := updateOptions.value.withCachedResolution(true)
    )

  def newProject(projectName: String): Project =
    newProject(projectName, file(projectName))

  def deduplicatedClasspath(classpaths: Keys.Classpath*): Keys.Classpath = {
    val merged = classpaths.foldLeft(Seq.empty[Attributed[File]]){(merged, cp) => merged ++ cp}
    merged.sortBy(_.data.getCanonicalPath).distinct
  }

  object TestCategory {
    private val pkg = "org.jetbrains.plugins.scala"
    private def cat(name: String) = s"$pkg.$name"

    val slowTests: String = cat("SlowTests")
    val perfOptTests: String = cat("PerfCycleTests")
    val highlightingTests: String = cat("HighlightingTests")
    val debuggerTests: String = cat("DebuggerTests")
    val scalacTests: String = cat("ScalacTests")
  }

  def pluginVersion: String =
    Option(System.getProperty("plugin.version")).getOrElse("SNAPSHOT")

  def replaceInFile(f: File, source: String, target: String): Unit = {
    if (!(source == null) && !(target == null)) {
      IO.writeLines(f, IO.readLines(f) map { _.replace(source, target) })
    }
  }

  def createRunnerProject(from: ProjectReference, name: String): Project =
    newProject(name, file(s"target/tools/$name"))
      .dependsOn(from % Provided)
      .settings(
        dumpDependencyStructure := null,
        products := packagePlugin.in(from).value :: Nil,
        packageMethod := org.jetbrains.sbtidea.Keys.PackagingMethod.Skip(),
        unmanagedJars in Compile := ideaMainJars.value,
        unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"
      )

  def patchPluginXML(f: File): File = {
    val tmpFile = java.io.File.createTempFile("plugin", ".xml")
    IO.copyFile(f, tmpFile)
    replaceInFile(tmpFile, "VERSION", pluginVersion)
    tmpFile
  }
}
