import sbt.{Def, _}
import Keys._
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.packaging.PackagingKeys._

import scala.language.implicitConversions
import scala.language.postfixOps

object Common {
  private val globalJavacOptionsCommon = Seq(
    "-Xlint:unchecked"
  )
  private val globalScalacOptionsCommon = Seq(
    "-explaintypes",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint:serial",
    "-Ymacro-annotations",
    "-Xfatal-warnings",
    "-language:existentials",
    "-Ytasty-reader",
  )

  // options for modules which classes can only be used in IDEA process (uses JRE 11)
  val globalJavacOptions : Seq[String] = globalJavacOptionsCommon ++ Seq("--release", "17")
  val globalScalacOptions: Seq[String] = globalScalacOptionsCommon ++ Seq("--release", "17")

  // options for modules which classes can be used outside IDEA process with arbitrary JVM version, e.g.:
  //  - in JPS process (JDK is calculated based on project & module JDK)
  //  - in Compile server (by default used project JDK version, can be explicitly changed by user)
  val outOfIDEAProcessJavacOptions : Seq[String] = globalJavacOptionsCommon ++ Seq("--release", "8")
  val outOfIDEAProcessScalacOptions: Seq[String] = globalScalacOptionsCommon ++ Seq("--release", "8")

  val headCommitSum: String =
    scala.sys.process.Process("git rev-parse HEAD").!!.trim.take(7)

  val compilationCacheSettings: Seq[Def.Setting[_]] = Seq(
    Compile / remoteCacheId := headCommitSum,
    Compile / pushRemoteCacheConfiguration ~= (_.withOverwrite(true)),
    Test / remoteCacheId := headCommitSum,
    Test / pushRemoteCacheConfiguration ~= (_.withOverwrite(true))
  )

  def newProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      name := projectName,
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      dependencyOverrides += "org.scala-lang" % "scala3-library_3" % "3.2.1-RC2", // TODO Workaround for SCL-18866
      (Compile / javacOptions) := globalJavacOptions,
      (Compile / scalacOptions) := globalScalacOptions,
      (Compile / unmanagedSourceDirectories) += baseDirectory.value / "src",
      (Test / unmanagedSourceDirectories) += baseDirectory.value / "test",
      (Test / unmanagedResourceDirectories) += baseDirectory.value / "testdata",
      libraryDependencies ++= Seq(Dependencies.junitInterface),
      updateOptions := updateOptions.value.withCachedResolution(true),
      intellijMainJars := intellijMainJars.value.filterNot(file => Dependencies.excludeJarsFromPlatformDependencies(file.data)),
      intellijPlugins += "com.intellij.java".toPlugin,
      pathExcludeFilter := excludePathsFromPackage _,
      (Test / testOptions) += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "20"),
      (Test / testFrameworks) := (Test / testFrameworks).value.filterNot(_.implClassNames.exists(_.contains("org.scalatest"))),
      (Test / scalacOptions) += "-Xmacro-settings:enable-expression-tracers",
      compilationCacheSettings
    )

  implicit class ProjectOps(private val project: Project) extends AnyVal {
    def withCompilerPluginIn(plugin: Project): Project = project
      .dependsOn(
        plugin % Provided
      )
      .settings(
        Compile / scalacOptions ++= Seq(
          s"-Xplugin:${(plugin / Compile / classDirectory).value}",
          s"-Xplugin-require:${(plugin / name).value}")
      )
  }

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

    val fileSetTests: String = cat("FileSetTests")
    val completionTests: String = cat("CompletionTests")
    val editorTests: String = cat("EditorTests")
    val slowTests: String = cat("SlowTests")
    val debuggerTests: String = cat("DebuggerTests")
    val scalacTests: String = cat("ScalacTests")
    val typecheckerTests: String = cat("TypecheckerTests")
    val testingSupportTests: String = cat("TestingSupportTests")
    val worksheetEvaluationTests: String = cat("WorksheetEvaluationTests")
    val highlightingTests: String = cat("HighlightingTests")
    val randomTypingTests: String = cat("RandomTypingTests")
    val flakyTests: String = cat("FlakyTests")
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
