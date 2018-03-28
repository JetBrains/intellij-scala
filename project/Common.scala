import sbt._
import Keys._
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

  lazy val homePrefix = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)

  def ivyHomeDir: File =
    Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)

  def commonTestSettings(packagedPluginDir: SettingKey[File]): Seq[Setting[_]] = Seq(
    fork in Test := true,
    parallelExecution := false,
    logBuffered := false,
    javaOptions in Test := Seq(
      "-Xms128m",
      "-Xmx4096m",
      "-server",
      "-ea",
      s"-Didea.system.path=${testSystemDir.value}",
      s"-Didea.config.path=${testConfigDir.value}",
      s"-Dsbt.ivy.home=$ivyHomeDir",
      s"-Dplugin.path=${packagedPluginDir.value}"
      // to enable debugging of tests running in external sbt instance
//      ,"-agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend=y"
    ),
    envVars in Test += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes"
  )
}
