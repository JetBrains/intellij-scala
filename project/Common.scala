import sbt._
import Keys._
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.packaging.PackagingKeys._

import scala.language.implicitConversions
import scala.language.postfixOps

object Common {
  lazy val communityFullClasspath: TaskKey[Classpath] =
    taskKey[Classpath]("scalaCommunity module's fullClasspath in Compile and Test scopes")

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
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-language:existentials"
  )

  // options for modules which classes can only be used in IDEA process (uses JRE 11)
  val globalJavacOptions : Seq[String] = globalJavacOptionsCommon ++ Seq(
    "-source", "11",
    "-target", "11"
  )
  val globalScalacOptions: Seq[String] = globalScalacOptionsCommon ++ Seq(
    // there is a bug in scalac 2.13.4 https://github.com/scala/bug/issues/12340
    // it stops us from using -target:11
    // (it's reproduced in some places in Scala Plugin, e.g. in org.jetbrains.plugins.scala.lang.psi.types.ScalaConformance.LeftConformanceVisitor.visitTypeParameterType
    // After the fix we would require to update to a newer 2.13.x version
    "-target:8"
  )

  // options for modules which classes can be used outside IDEA process with arbitrary JVM version, e.g.:
  //  - in JPS process (JDK is calculated based on project & module JDK)
  //  - in Compile server (by default used project JDK version, can be explicitly changed by user)
  val outOfIDEAProcessJavacOptions : Seq[String] = globalJavacOptionsCommon ++ Seq(
    "-source", "8",
    "-target", "8"
    // TODO: replace "-source" & "-target" with "--release"
    //  after this is fixed: https://youtrack.jetbrains.com/issue/SCL-17597
    //  (Scala Plugin should importing of language level and target bytecode level from --release option)
    //  Wait until this is available in at least 2 plugin Release versions: current release and under-development version.
    //  For why we should better use --release flag instead of just "-source" and "-target", please see
    //  http://openjdk.java.net/jeps/247
    //  https://stackoverflow.com/questions/43102787/what-is-the-release-flag-in-the-java-9-compiler/43103038#43103038
    //  https://blogs.oracle.com/darcy/new-javac-warning-for-setting-an-older-source-without-bootclasspath
    //  In short: to prevent accidental use of Java 11 API which can produce a runtime error
    //"--release", "8"
  )
  val outOfIDEAProcessScalacOptions: Seq[String] = globalScalacOptionsCommon ++ Seq(
    "-target:8",
    "--release", "8"
  )

  def newProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      name := projectName,
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      javacOptions in Compile := globalJavacOptions,
      scalacOptions in Compile := globalScalacOptions,
      unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
      unmanagedSourceDirectories in Test += baseDirectory.value / "test",
      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
      unmanagedResourceDirectories in Test += baseDirectory.value / "testResources",
      libraryDependencies ++= Seq(Dependencies.junitInterface),
      updateOptions := updateOptions.value.withCachedResolution(true),
      intellijMainJars := intellijMainJars.value.filterNot(file => Dependencies.excludeJarsFromPlatformDependencies(file.data)),
      intellijPlugins += "com.intellij.java".toPlugin,
      pathExcludeFilter := excludePathsFromPackage,
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "20"),
      testFrameworks in Test := (testFrameworks in Test).value.filterNot(_.implClassNames.exists(_.contains("org.scalatest"))),
      scalacOptions in Test += "-Xmacro-settings:enable-expression-tracers"
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
    val highlightingTests: String = cat("HighlightingTests")
    val debuggerTests: String = cat("DebuggerTests")
    val scalacTests: String = cat("ScalacTests")
    val typecheckerTests: String = cat("TypecheckerTests")
    val testingSupportTests: String = cat("TestingSupportTests")
    val worksheetEvaluationTests: String = cat("WorksheetEvaluationTests")
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
