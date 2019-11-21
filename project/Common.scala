import sbt._
import Keys._
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.packaging.PackagingKeys._

import scala.language.implicitConversions
import scala.language.postfixOps

object Common {
  lazy val communityFullClasspath: TaskKey[Classpath] =
    taskKey[Classpath]("scalaCommunity module's fullClasspath in Compile and Test scopes")

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
      updateOptions := updateOptions.value.withCachedResolution(true),
      intellijMainJars := intellijMainJars.value.filterNot(file => Dependencies.excludeJarsFromPlatformDependencies(file.data)),
      intellijInternalPlugins += "java",
      pathExcludeFilter := excludePathsFromPackage,
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "20"),
      testFrameworks in Test := (testFrameworks in Test).value.filterNot(_.implClassNames.exists(_.contains("org.scalatest")))
    )

  def excludePathsFromPackage(path: java.nio.file.Path): Boolean = {
    // TODO we should generally filter META-INF when merging jars

    val parent = path.getParent
    val filename = path.getFileName.toString

    // exclude .../META-INF/*.RSA *.SF
    parent != null && parent.toString == "META-INF" &&
      (filename.endsWith(".RSA") || filename.endsWith(".SF"))
  }

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
    val typecheckerTests: String = cat("TypecheckerTests")
    val testingSupportTests: String = cat("TestingSupportTests")
    val worksheetEvaluationTests: String = cat("WorksheetEvaluationTests")
  }

  def pluginVersion: String =
    Option(System.getProperty("plugin.version")).getOrElse("SNAPSHOT")

  def replaceInFile(f: File, source: String, target: String): Unit = {
    if (!(source == null) && !(target == null)) {
      IO.writeLines(f, IO.readLines(f) map { _.replace(source, target) })
    }
  }

  def patchPluginXML(f: File): File = {
    val tmpFile = java.io.File.createTempFile("plugin", ".xml")
    IO.copyFile(f, tmpFile)
    replaceInFile(tmpFile, "VERSION", pluginVersion)
    tmpFile
  }
}
