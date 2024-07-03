import kotlin.Keys.{kotlinRuntimeProvided, kotlinVersion, kotlincJvmTarget}
import kotlin.KotlinPlugin
import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import sbt.Keys.*
import sbt.Project.projectToRef
import sbt.{Def, *}

import java.nio.file.Path

object Common {
  private val globalJavacOptionsCommon = Seq(
    "-Xlint:unchecked",
    "-Xlint:deprecation"
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
  // NOTE: we rely on the fact that javac & scalac use the same compiler option name,
  // though strictly speaking they have different types (they represent settings for different compilers)
  private val globalIdeaProcessReleaseOptions: Seq[String] = Seq("--release", "17")
  val globalJavacOptions             : Seq[String] = globalJavacOptionsCommon ++ globalIdeaProcessReleaseOptions
  val globalScalacOptions            : Seq[String] = globalScalacOptionsCommon ++ globalIdeaProcessReleaseOptions
  val globalScala3ScalacOptions      : Seq[String] = globalScala3ScalacOptionsCommon ++ globalIdeaProcessReleaseOptions

  // options for modules which classes can be used outside IDEA process with arbitrary JVM version, e.g.:
  //  - in JPS process (JDK is calculated based on project & module JDK)
  //  - in Compile server (by default used project JDK version, can be explicitly changed by user)
  private val globalExternalProcessReleaseOptions: Seq[String] = Seq("--release", "8")
  val outOfIDEAProcessJavacOptions       : Seq[String] = globalJavacOptionsCommon ++ globalExternalProcessReleaseOptions
  val outOfIDEAProcessScalacOptions      : Seq[String] = globalScalacOptionsCommon ++ globalExternalProcessReleaseOptions

  val projectDirectoriesSettings: Seq[Setting[?]] = Seq(
    // production sources
    Compile / sourceDirectory := baseDirectory.value / "src", // we put all source files in <subproject_dir>/src
    Compile / unmanagedSourceDirectories := Seq((Compile / sourceDirectory).value),
    // test sources
    Test / sourceDirectory := baseDirectory.value / "test", // we put all test source files in <subproject_dir>/test
    Test / unmanagedSourceDirectories := Seq((Test / sourceDirectory).value),
    //NOTE: this almost duplicates the logic from sbt-idea-plugin (see org.jetbrains.sbtidea.Init)
    //but it uses `:=` instead of `+=` to remove standard resource directories, which intersect with source directories
    // production resources
    Compile / resourceDirectory := baseDirectory.value / "resources",
    Compile / unmanagedResourceDirectories := Seq((Compile / resourceDirectory).value),
    // test resources
    //Note: we don't mark "testdata" as "test resources", because test data files are not test resources.
    //Those directories don't contain files which that should be copied `target/scala-2.13/test-classes
    Test / resourceDirectory := baseDirectory.value / "testResources",
    Test / unmanagedResourceDirectories := Seq((Test / resourceDirectory).value)
  )

  val NoSourceDirectories: Seq[Def.SettingsDefinition] = Seq(
    Compile / sourceDirectories := Nil,
    Compile / managedSourceDirectories := Nil,
    Compile / unmanagedSourceDirectories := Nil,
    Test / sourceDirectories := Nil,
    Test / managedSourceDirectories := Nil,
    Test / unmanagedSourceDirectories := Nil,
  )

  private val NewProjectBaseSettings: Seq[Setting[?]] = Seq(
    organization := "JetBrains",
    scalaVersion := Versions.scalaVersion,
    (Compile / javacOptions) := globalJavacOptions,
    (Compile / scalacOptions) := globalScalacOptions,
    updateOptions := updateOptions.value.withCachedResolution(true),
    instrumentThreadingAnnotations := true,
    libraryDependencies ++= Seq(
      //jetbrains annotations library is quite minimalistic, it's required for @Nullable/@NotNull/@Nls/etc.. annotations
      Dependencies.jetbrainsAnnotations % Provided,
      Dependencies.junit % Test,
      Dependencies.junitInterface % Test,
    ),
  ) ++ projectDirectoriesSettings

  val intellijPluginsScopeFilter: ScopeFilter =
    ScopeFilter(inDependencies(ThisProject, includeRoot = false))

  //Common settings for Community & Ultimate main projects
  val MainProjectSettings: Seq[Def.SettingsDefinition] = Seq(
    sourcesInBase   := false,
    packageMethod := PackagingMethod.Standalone(),
    libraryDependencies ++= Seq(
      Dependencies.scalaLibrary,
      Dependencies.scala3Library,
      //Original commit message:
      //scala-reflect.jar could be excluded from package mappings in scala-impl, because jars from scala-lang are not
      // package by default in non-root modules. It was non-deterministic and happened on my machine, but not on buildserver.
      //https://github.com/JetBrains/sbt-idea-plugin/blob/d7d8a421cc4ff10ea723ce116a79cb4491d7e38d/packaging/src/main/scala/org/jetbrains/sbtidea/packaging/PackagingKeysInit.scala#L26
      Dependencies.scalaReflect,
      Dependencies.scalaXml
    ),
    packageLibraryMappings := Seq(
      Dependencies.scalaLibrary   -> Some("lib/scala-library.jar"),
      Dependencies.scala3Library  -> Some("lib/scala3-library_3.jar"),
      Dependencies.scalaReflect   -> Some("lib/scala-reflect.jar"),
      Dependencies.scalaXml       -> Some("lib/scala-xml.jar"),
    ),
    intellijPlugins := intellijPlugins.all(intellijPluginsScopeFilter).value.flatten.distinct,
    intellijExtraRuntimePluginsInTests := Seq(
      //Below are some other useful plugins which you might be interested to inspect
      //We don't have any dependencies on those plugins, however sometimes it might be useful to see how some features are implemented in them plugin.
      //You can uncomment any of them locally

      //This bundled plugin contains some internal development tools such as "View Psi Structure" action
      //(note there is also PsiViewer plugin, but it's a different plugin)
      //"com.intellij.dev".toPlugin,

      "org.jetbrains.kotlin".toPlugin
    ),
  )

  def newPlainScalaProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      NewProjectBaseSettings
    ).settings(
      name := projectName,
      intellijMainJars := Seq.empty,
      intellijTestJars := Seq.empty,
      intellijPlugins := Seq.empty,
    )

  def newProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      NewProjectBaseSettings
    ).settings(
      name := projectName,
      intellijMainJars := intellijMainJars.value.filterNot(file => Dependencies.excludeJarsFromPlatformDependencies(file)),
      intellijPlugins += "com.intellij.java".toPlugin,
      pathExcludeFilter := excludePathsFromPackage _,
      //needed for BSP module (maybe move it there then?)
      (Test / testOptions) += Tests.Argument(TestFrameworks.ScalaCheck, "-maxSize", "20"),
      (Test / testFrameworks) := (Test / testFrameworks).value.filterNot(_.implClassNames.exists(_.contains("org.scalatest")))
    )

  /**
   * ATTENTION: Kotlin modules should be used only in those cases when it is impossible or very hard to extend
   * platform functionality in Scala (due to the inherent requirements of the platform and only for the interop)
   */
  def newProjectWithKotlin(projectName: String): Project =
    newProjectWithKotlin(projectName, file(projectName))

  /**
   * ATTENTION: Kotlin modules should be used only in those cases when it is impossible or very hard to extend
   * platform functionality in Scala (due to the inherent requirements of the platform and only for the interop)
   */
  def newProjectWithKotlin(projectName: String, base: File): Project =
    newProject(projectName, base)
      .enablePlugins(KotlinPlugin)
      .settings(
        // NOTE: check community/.idea/libraries/kotlin_stdlib.xml in intellij monorepo when updating intellijVersion
        // NOTE: keep versions in sync with ultimate/.idea/kotlinc.xml and community/.idea/kotlinc.xml
        kotlinVersion := "1.9.24",
        kotlincJvmTarget := "17",
        kotlinRuntimeProvided := true
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
   * TODO: can we take the base classpath from the product.info?
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
    val bundleSortingTests: String = cat("BundleSortingTests")
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
