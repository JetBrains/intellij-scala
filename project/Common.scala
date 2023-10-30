import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import sbt.Keys.*
import sbt.Project.projectToRef
import sbt.{Def, *}

import java.nio.file.Path

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
    "-Xlint:nullary-unit",
    "-Xfatal-warnings",
    "-language:existentials",
    "-Ytasty-reader",
    "-Wunused:nowarn"
  )
  private val globalScala3ScalacOptionsCommon = Seq(
    "-deprecation",
    "-explain",
    "-feature",
    "-unchecked",
    "-Werror",
    "-Wunused:implicits,imports",
  )

  // options for modules which classes can only be used in IDEA process (uses JRE 17)
  val globalIdeaProcessReleaseOptions: Seq[String] = Seq("--release", "17")
  val globalJavacOptions             : Seq[String] = globalJavacOptionsCommon ++ globalIdeaProcessReleaseOptions
  val globalScalacOptions            : Seq[String] = globalScalacOptionsCommon ++ globalIdeaProcessReleaseOptions
  val globalScala3ScalacOptions      : Seq[String] = globalScala3ScalacOptionsCommon ++ globalIdeaProcessReleaseOptions

  // options for modules which classes can be used outside IDEA process with arbitrary JVM version, e.g.:
  //  - in JPS process (JDK is calculated based on project & module JDK)
  //  - in Compile server (by default used project JDK version, can be explicitly changed by user)
  val globalExternalProcessReleaseOptions: Seq[String] = Seq("--release", "8")
  val outOfIDEAProcessJavacOptions       : Seq[String] = globalJavacOptionsCommon ++ globalExternalProcessReleaseOptions
  val outOfIDEAProcessScalacOptions      : Seq[String] = globalScalacOptionsCommon ++ globalExternalProcessReleaseOptions

  private val NewProjectBaseSettings: Seq[Def.SettingsDefinition] = Seq(
    organization := "JetBrains",
    scalaVersion := Versions.scalaVersion,
    (Compile / javacOptions) := globalJavacOptions,
    (Compile / scalacOptions) := globalScalacOptions,
    //TODO: it shouldn't append but should completely replace the default source dirs, otherwise there will be overlapping dirs: src and src/scala
    (Compile / unmanagedSourceDirectories) += baseDirectory.value / "src",
    (Test / unmanagedSourceDirectories) += baseDirectory.value / "test",
    updateOptions := updateOptions.value.withCachedResolution(true),
  )

  def newPlainScalaProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      NewProjectBaseSettings *
    ).settings(
      name := projectName,
      intellijMainJars := Seq.empty,
      intellijPlugins := Seq.empty,
    )

  def newProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      NewProjectBaseSettings *
    ).settings(
      name := projectName,
      //Note: we explicitly don't mark "testdata" directories as "test resources", because they are not test resources
      // (those directories don't contain files which are supposed to be copied to `target/scala-2.13/test-classes
      //(Test / unmanagedResourceDirectories) += baseDirectory.value / "testdata",
      libraryDependencies ++= Seq(
        Dependencies.jetbrainsAnnotations % Provided,
        Dependencies.junit % Test,
        Dependencies.junitInterface % Test,
      ),
      intellijMainJars := intellijMainJars.value.filterNot(file => Dependencies.excludeJarsFromPlatformDependencies(file.data)),
      intellijPlugins += "com.intellij.java".toPlugin,
      pathExcludeFilter := excludePathsFromPackage _,
      (Test / testOptions) += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "20"),
      (Test / testFrameworks) := (Test / testFrameworks).value.filterNot(_.implClassNames.exists(_.contains("org.scalatest")))
    )

  /**
   * Manually build classpath for the JPS module.
   * Code from JPS modules is executed in JPS process which has a separate classpath.
   *
   * The classpath construction logic can be found here:
   *  - com.intellij.compiler.server.impl.BuildProcessClasspathManager.getBuildProcessClasspath
   *  - org.jetbrains.jps.cmdline.ClasspathBootstrap.getBuildProcessApplicationClasspath
   *  - com.intellij.compiler.server.impl.BuildProcessClasspathManager.getBuildProcessPluginsClasspath
   *
   * An easy practical way too see which classpath is actually used is to place a breakpoint inside
   * BuildProcessClasspathManager.getBuildProcessClasspath
   *
   * Note that JPS process will contain classpath from other plugins as well.
   * Currently only base classes from Java & Platform are required for Scala Plugin
   *
   * @todo we might also use this classpath for community/scala/compiler-jps module. But before that it should be refactored.
   *       Currently, the module contains code which might be executed in Scala Compile Server, not only in JPS.
   *       We should split it into separate modules with oun classpathes
   */
  def jpsClasspath: Def.Initialize[Task[Classpath]] = Def.task {
    val intellijLibDir = intellijBaseDirectory.value / "lib"
    val intellijPluginsDir = intellijBaseDirectory.value / "plugins"

    /** see also org.jetbrains.plugins.scala.compiler.CompileServerLauncher.compileServerJars */
    val platformJarNames = Seq(
      "util.jar",
      "util-8.jar",
      "util_rt.jar",
      "protobuf.jar",
      "jps-model.jar",
      "forms_rt.jar",
      "idea_rt.jar",
    )
    //If you need any extra plugin dependencies, add the jars here
    val pluginsJarPaths = Seq(
      "java/lib/jps-builders.jar",
      "java/lib/jps-builders-6.jar",
      "java/lib/jps-javac-extension.jar",
      "java/lib/javac2.jar",
      "java/lib/aether-dependency-resolver.jar",
    )

    val platformJarsFiles: Seq[File] = platformJarNames.map(intellijLibDir / _)
    val pluginJarsFiles: Seq[File] = pluginsJarPaths.map(intellijPluginsDir / _)

    (platformJarsFiles ++ pluginJarsFiles).classpath
  }

  def compilerSharedClasspath: Def.Initialize[Task[Classpath]] = Def.task {
    val intellijLibDir = intellijBaseDirectory.value / "lib"

    val platformJarNames = Seq(
      "app.jar",
      "app-client.jar",
      "util.jar",
      "util-8.jar",
      "util_rt.jar",
    )

    platformJarNames.map(intellijLibDir / _).classpath
  }

  implicit class ProjectOps(private val project: Project) extends AnyVal {
    def withCompilerPluginIn(plugin: Project): Project =
      withCompilerPluginIn(projectToRef(plugin))

    def withCompilerPluginIn(plugin: ProjectReference): Project = project
      .dependsOn(
        plugin % Provided
      )
      .settings(
        Compile / scalacOptions ++= Seq(
          s"-Xplugin:${(plugin / Compile / classDirectory).value}",
          s"-Xplugin-require:${(plugin / name).value}")
      )
  }

  private def excludePathsFromPackage(path: java.nio.file.Path): Boolean =
    `is signature file in META-INF`(path)

  //This filtering was originally added within SCL-14474
  //TODO we should generally filter META-INF when merging jars
  private def `is signature file in META-INF`(path: Path): Boolean = {
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
    val compilationTests: String = cat("CompilationTests")
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
