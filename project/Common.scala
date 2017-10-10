import sbt._
import Keys._
import scala.language.implicitConversions
import scala.language.postfixOps

object Common {
  def newProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      name := projectName,
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
      unmanagedSourceDirectories in Test += baseDirectory.value / "test",
      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
      libraryDependencies += Dependencies.junitInterface,
      updateOptions := updateOptions.value.withCachedResolution(true)
    )

  def newProject(projectName: String): Project =
    newProject(projectName, file(projectName))

  object TestCategory {
    private val pkg = "org.jetbrains.plugins.scala"
    private def cat(name: String) = s"$pkg.$name"

    val slowTests: String = cat("SlowTests")
    val perfOptTests: String = cat("PerfCycleTests")
    val highlightingTests: String = cat("HighlightingTests")
    val debuggerTests: String = cat("DebuggerTests")
    val scalacTests: String = cat("ScalacTests")
  }

  val testConfigDir: File =
    Path.userHome / ".IdeaData" / "IDEA-15" / "scala" / "test-config"

  val testSystemDir: File =
    Path.userHome / ".IdeaData" / "IDEA-15" / "scala" / "test-system"

  def ivyHomeDir: File =
    Option(System.getProperty("sbt.ivy.home")).fold(Path.userHome / ".ivy2")(file)

  def commonTestSettings(packagedPluginDir: SettingKey[File]): Seq[Setting[_]] = Seq(
    fork in Test := true,
    parallelExecution := false,
    logBuffered := false,
    javaOptions in Test := Seq(
      "-Xms128m",
      "-Xmx4096m",
      "-ea",
      s"-Didea.system.path=$testSystemDir",
      s"-Didea.config.path=$testConfigDir",
      s"-Dsbt.ivy.home=$ivyHomeDir",
      s"-Dplugin.path=${packagedPluginDir.value}"
      // to enable debugging of tests running in external sbt instance
//      ,"-agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend=y"
    ),
    envVars in Test += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes"
  )
}
