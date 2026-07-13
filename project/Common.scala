import AttributedClasspathUtils.buildIntellijSdkSubsetAttributedClasspath
import CompilationCache.compilationCacheSettings
import com.github.sbt.junit.jupiter.sbt.Import.JupiterKeys
import org.jetbrains.sbt.kotlin.Keys.{kotlinRuntimeProvided, kotlinVersion, kotlincJvmTarget}
import org.jetbrains.sbt.kotlin.KotlinPlugin
import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import sbt.*
import sbt.Keys.*
import sbt.Project.projectToRef

import java.nio.file.Path

object Common {
  final val JetBrains = "JetBrains"

  /**
   * Tells the IntelliJ test framework to write per-test log dumps to separate files instead of printing the
   * full Debug/Trace log dump into the test process output before failure details.
   *
   * This keeps IDEA and agent-driven test output more focused on failures. IDEA console output can otherwise
   * be truncated before its "DEBUG Log" folding helps, and large inline log dumps can consume agent context quickly.
   * The context-saving effect is based on local experiments rather than a dedicated evaluation.
   */
  final val SplitTestLogsVmOption = "-Didea.split.test.logs=true"

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
    "-language:existentials",
    "-Ytasty-reader",
    "-Wunused:nowarn",
    // NOTE: we agreed to disable "fatal warnings" in Scala Plugin repo after an exhaustive discussion in the team.
    // They are useful as code-quality signal, but treating them as compilation errors is not convenient in practice.
    // It hurts iterative local development and can block TeamCity from running tests.
    // Instead, we make warnings visible (IDEA-391176) and track/report them on CI in a separate view.
    //"-Xfatal-warnings",
  )
  private val globalScala3ScalacOptionsCommon = Seq(
    "-deprecation",
    "-explain",
    "-feature",
    "-unchecked",
    "-Wunused:implicits,imports",
    "-Wconf:msg=Non local returns are no longer supported:s", // I like local returns!!!
    "-Wconf:msg=Alphanumeric method isInstance is not declared infix:s", // and everyone loves infix notation!
    "-Wconf:msg=method .* is eta-expanded even though .* does not have the @FunctionalInterface annotation:s", // bullshit warning
    // NOTE: The same comment as for "-Xfatal-warnings" in Scala 2 (see above)
    //"-Werror",
  )

  // options for modules which classes can only be used in IDEA process (uses JDK 25)
  // NOTE: we rely on the fact that javac & scalac use the same compiler option name,
  // though strictly speaking they have different types (they represent settings for different compilers)
  private val globalIdeaProcessReleaseOptions: Seq[String] = Seq("--release", "25")
  val globalJavacOptions: Seq[String] = globalJavacOptionsCommon ++ globalIdeaProcessReleaseOptions
  val globalScalacOptions: Seq[String] = globalScalacOptionsCommon ++ globalIdeaProcessReleaseOptions
  val globalScala3ScalacOptions: Seq[String] = globalScala3ScalacOptionsCommon ++ globalIdeaProcessReleaseOptions

  // options for modules which classes can be used outside IDEA process with arbitrary JVM version, e.g.:
  //  - in JPS process (JDK is calculated based on project & module JDK)
  //  - in Compile server (by default used project JDK version, can be explicitly changed by user)
  private val globalExternalProcessReleaseOptions: Seq[String] = Seq("--release", "8")

  // JDK 25 warns when we compile Java sources using "--release 8" that targeting JDK 8 will not be possible in the
  // future. JDK 25 is the next LTS, so we should be fine for now.
  private val suppressObsoleteSourceTarget8: String = "-Xlint:-options"

  val outOfIDEAProcessJavacOptions: Seq[String] = globalJavacOptionsCommon ++ globalExternalProcessReleaseOptions :+ suppressObsoleteSourceTarget8
  val outOfIDEAProcessScalacOptions: Seq[String] = globalScalacOptionsCommon ++ globalExternalProcessReleaseOptions
  val outOfIDEAProcessScala3ScalacOptions: Seq[String] = globalScala3ScalacOptionsCommon ++ globalExternalProcessReleaseOptions

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

  val NoSourceDirectories: Seq[Setting[?]] = {
    val settings = Seq(
      sourceDirectories := Seq.empty,
      managedSourceDirectories := Seq.empty,
      unmanagedSourceDirectories := Seq.empty
    )
    inConfig(Compile)(settings) ++ inConfig(Test)(settings)
  }

  // Adds dependency on Kotlin plugin
  // NOTE: this val might not be used but is handy to keep here in case one needs to debug Kotlin code
  private lazy val AddKotlinPluginDependenciesSettings: Seq[Setting[?]] = Seq(
    intellijPlugins += "org.jetbrains.kotlin".toPlugin,
    // Kotlin plugin jars bundle some Kotlin Analysis Api classes, however, no sources are bundled in the IJ sources archive
    libraryDependencies ++= KotlinAnalysisApiIdeSourcesDependencies,
  )

  private lazy val KotlinAnalysisApiIdeSourcesDependencies: Seq[ModuleID] = {
    // Unfortunately, we can't automatically detect this version. It's not published in any artifacts
    // NOTE: take the latest version from (Use proper branch)
    // https://github.com/JetBrains/intellij-community/blob/master/.idea/libraries/kotlinc_analysis_api.xml
    val KotlinAnalysisApiVersion = "2.2.20-ij252-17"
    Seq(
      "org.jetbrains.kotlin" % "analysis-api-fe10-for-ide" % KotlinAnalysisApiVersion,
      "org.jetbrains.kotlin" % "analysis-api-for-ide" % KotlinAnalysisApiVersion,
      "org.jetbrains.kotlin" % "analysis-api-impl-base-for-ide" % KotlinAnalysisApiVersion,
      "org.jetbrains.kotlin" % "analysis-api-k2-for-ide" % KotlinAnalysisApiVersion,
      "org.jetbrains.kotlin" % "analysis-api-platform-interface-for-ide" % KotlinAnalysisApiVersion,
      "org.jetbrains.kotlin" % "analysis-api-standalone-for-ide" % KotlinAnalysisApiVersion,
      "org.jetbrains.kotlin" % "kotlin-compiler-common-for-ide" % KotlinAnalysisApiVersion,
    ).map(_
      .withSources() // `withSources` instead of `sources` in order the library is visible in the project view
      .intransitive() // It seems each jar is self-contained, but the pom metadata contains dependencies, so we need to use "intransitive" to avoid failed download errors
    )
  }

  lazy val KotlinAnalysisApiIdeSourcesDependencies_ProvidedScope: Seq[ModuleID] =
    KotlinAnalysisApiIdeSourcesDependencies.map(_ % Provided)

  private val NewProjectBaseSettings: Seq[Setting[?]] = Seq(
    organization := JetBrains,
    scalaVersion := Versions.scalaVersion,
    (Compile / javacOptions) := globalJavacOptions,
    (Compile / scalacOptions) := globalScalacOptions,
    updateOptions := updateOptions.value.withCachedResolution(true),
    instrumentThreadingAnnotations := true,
    libraryDependencies ++= Seq(
      //jetbrains annotations library is quite minimalistic, it's required for @Nullable/@NotNull/@Nls/etc.. annotations
      Dependencies.jetbrainsAnnotations % Provided,
      Dependencies.junit % Test,
      Dependencies.junitParams % Test,
      Dependencies.junitInterface % Test,
      Dependencies.jupiterInterface(JupiterKeys.jupiterVersion.value) % Test,
      Dependencies.junitJupiterParams(JupiterKeys.junitJupiterVersion.value) % Test
    ),
    // There is no need to mark this as a % Test dependency, it is marked as a dependency of an internal config
    // of sbt-idea-plugin. These libraries are intentionally added only to the generated JUnit test templates classpath,
    // but not to the sbt test classpath. #SCL-25373
    intellijExtraJUnitTemplateLibraryDependencies +=
      Dependencies.junitVintageEngine(JupiterKeys.junitVintageVersion.value),
  ) ++ projectDirectoriesSettings ++
    compilationCacheSettings

  val intellijPluginsScopeFilter: ScopeFilter =
    ScopeFilter(inDependencies(ThisProject, includeRoot = false))

  //Common settings for Community & Ultimate main projects
  val MainProjectSettings: Seq[Setting[?]] = Seq(
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
    Test / javaOptions += "-Didea.log.leaked.projects.in.tests=false",
    Test / customIntellijVMOptions ~= { opts =>
      opts.withExtraOptions(Seq(
        "-Dintellij.testFramework.junit5.skip.test.application.dispose=true",
        SplitTestLogsVmOption,
      ))
    }
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
      intellijMainJars ~= { _.filterNot(Dependencies.excludeJarsFromPlatformDependencies).filter(_.exists()) },
      intellijTestJars ~= { _.filter(_.exists()) },
      intellijPlugins ++= Seq(
        "com.intellij.java",
        // required for Java plugin (IJPL-244879)
        "intellij.todo.plugin",
        // required for Java plugin (IJPL-245969), but also for Scala (SCL-25534)
        "intellij.libraries.misc.plugin",
        // TODO: add these plugins only in the modules where they are needed
        "intellij.java.aetherDependencyResolver.plugin",
        "intellij.testRunner.plugin"
      ).map(_.toPlugin),
      pathExcludeFilter := excludePathsFromPackage _
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
        kotlinVersion := "2.4.10-RC2",
        kotlincJvmTarget := "25",
        kotlinRuntimeProvided := true,
        resolvers += DependencyResolvers.IntelliJDependencies,
      )

  implicit class ProjectOps(private val project: Project) extends AnyVal {

    /**
     * Manually build the classpath for the JPS module.
     * Code from JPS modules is executed in the JPS process, which has a separate classpath.
     *
     * @note this classpath is only required to properly compile the module
     *       (in order we do not accidentally use any classes that are not available in the JPS process)<br>
     *       At runtime the classpath will be constructed in by Platform.
     * @see [[IntellijSdkSubsetInfo.Jps]]
     */
    def withJpsClasspath: Project = withIntellijSubsetDependency(IntellijSdkSubsetInfo.Jps)

    /**
     * Similar to [[withJpsClasspath]] but defines the classes that are used in both JPS and IntelliJ processes
     */
    def withJpsSharedClasspath: Project = withIntellijSubsetDependency(IntellijSdkSubsetInfo.JpsShared)

    private def withIntellijSubsetDependency(subsetInfo: IntellijSdkSubsetInfo): Project = {
      project.settings(
        // This line only registers information about special INTELLIJ-SDK-* libraries in the update report
        update := UpdateWithIDEAInjectionTasks2.getUpdateReportWithIntellijSdkSubsetModuleTask(subsetInfo).value,

        // This line adds the special INTELLIJ-SDK-* module (~library) to the classpath project classpath
        Compile / externalDependencyClasspath ++= {
          val buildInfo = productInfo.value.buildNumber
          val intellijBaseDir = intellijBaseDirectory.value
          val info = subsetInfo.toMaterialisedInfo(buildInfo, intellijBaseDir)

          buildIntellijSdkSubsetAttributedClasspath(info, Compile)
        }
      )
    }

    /**
     * @note Be careful when applying this to sbt subprojects.
     *       Any `Compile / scalacOptions := Seq(...)` specified after this method is called will completely override
     *       the scalac plugin, and it will not be applied.
     */
    def withCompilerPluginIn(plugin: Project): Project =
      withCompilerPluginIn(projectToRef(plugin))

    /**
     * @note Be careful when applying this to sbt subprojects.
     *       Any `Compile / scalacOptions := Seq(...)` specified after this method is called will completely override
     *       the scalac plugin, and it will not be applied.
     */
    def withCompilerPluginIn(plugin: ProjectReference): Project = project
      .dependsOn(
        plugin % Provided
      )
      .settings(
        // TODO Only Test / scalacOptions
        Compile / scalacOptions ++= Seq(
          s"-Xplugin:${(plugin / Compile / classDirectory).value}",
          s"-Xplugin-require:${(plugin / name).value}")
      )

    def projectWithTestsOnly: Project = project.settings(
      Compile / sourceDirectories := Nil,
      Compile / resourceDirectories := Nil,
      // Base settings define Compile / resourceDirectory; keep a value but hide it so lintUnused doesn't warn for test-only modules.
      Compile / resourceDirectory.withRank(KeyRanks.Invisible) := baseDirectory.value / "resources",
      Compile / unmanagedSourceDirectories := Nil,
      Compile / unmanagedResourceDirectories := Nil,
      // Packaging a module with tests only doesn't make sense
      packageMethod := PackagingMethod.Skip(),
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
    val merged = classpaths.foldLeft(Seq.empty[Attributed[File]]) { (merged, cp) => merged ++ cp }
    merged.sortBy(_.data.getCanonicalPath).distinct
  }

  object TestCategory {
    private val pkg = "org.jetbrains.plugins.scala"
    private def cat(name: String) = s"$pkg.$name"

    val fileSetTests: String = cat("FileSetTests")
    val compilationTestsZinc: String = cat("CompilationTests_Zinc")
    val compilationTestsIDEA: String = cat("CompilationTests_IDEA")
    val compilerHighlightingTests: String = cat("CompilerHighlightingTests")
    val completionTests: String = cat("CompletionTests")
    val editorTests: String = cat("EditorTests")
    val slowTests: String = cat("SlowTests")
    val slowTests2: String = cat("SlowTests2")
    val debuggerTests: String = cat("DebuggerTests")
    val debuggerEvaluationTests: String = cat("DebuggerEvaluationTests")
    val scalacTests: String = cat("ScalacTests")
    val typecheckerTests: String = cat("TypecheckerTests")
    val testingSupportTests: String = cat("TestingSupportTests")
    val textToTextTests: String = cat("TextToTextTests")
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
      IO.writeLines(f, IO.readLines(f).map(_.replace(source, target)))
    }
  }

  def patchPluginXML(f: File): File = {
    val tmpFile = java.io.File.createTempFile("plugin", ".xml")
    IO.copyFile(f, tmpFile)
    replaceInFile(tmpFile, "VERSION", pluginVersion)
    tmpFile
  }

  lazy val cleanAll: TaskKey[Unit] = taskKey("Cleans all modules")

  def cleanAllTask: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val structure = buildStructure.value
    val build = thisProjectRef.value.build
    val projects = structure.allProjectRefs(build)
    val scopeFilter = ScopeFilter(inProjects(projects*), inAnyConfiguration)
    clean.all(scopeFilter).map(_ => ())
  }

  lazy val verifyJpsSdkSubsetBytecode: TaskKey[Unit] =
    taskKey(s"Verify the IntelliJ SDK subset (JPS) classpaths contain no Java ${JpsSdkSubsetBytecodeVerifier.MaxAllowedJavaVersion + 1}+ bytecode (SCL-25518)")

  def verifyJpsSdkSubsetBytecodeTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = sLog.value
    val intellijBaseDir = intellijBaseDirectory.value
    val buildNumber = productInfo.value.buildNumber

    // Reference the subset definitions directly so this check always reflects their current contents.
    val subsets = Seq(IntellijSdkSubsetInfo.Jps, IntellijSdkSubsetInfo.JpsShared)
    val maxJava = JpsSdkSubsetBytecodeVerifier.MaxAllowedJavaVersion
    val result = JpsSdkSubsetBytecodeVerifier.verify(subsets, buildNumber, intellijBaseDir)

    if (result.suppressedViolations.nonEmpty) {
      val summary = result.suppressedViolations
        .groupBy(_.jar.getName)
        .toSeq
        .sortBy(_._1)
        .map { case (jarName, vs) => s"$jarName (${vs.size} classes up to Java ${vs.map(_.requiredJavaVersion).max})" }
        .mkString(", ")
      log.warn(s"JPS SDK subset bytecode: suppressed violations in known non-compliant jar(s) (SCL-25518): $summary")
    }

    if (result.hasProblems) {
      val missingSection =
        if (result.missingJars.nonEmpty)
          Some("Missing jars (IntellijSdkSubsetInfo paths are stale):\n" + result.missingJars
            .map(m => s"  - [${m.subsetName}] ${m.relativePath} (expected at ${m.file})")
            .mkString("\n"))
        else None
      val violationSection =
        if (result.violations.nonEmpty)
          Some(s"Bytecode requiring newer than Java $maxJava:\n" + result.violations
            .map(v => s"  - ${v.jar.getName} -> ${v.entry}: class major ${v.classMajorVersion} (Java ${v.requiredJavaVersion})")
            .mkString("\n"))
        else None
      val sections = Seq(missingSection, violationSection).flatten
      sys.error((s"JPS SDK subset bytecode verification failed (SCL-25518): code in these classpaths must run on Java $maxJava or older." +: sections).mkString("\n\n"))
    } else {
      val suppressedSuffix =
        if (result.suppressedViolations.nonEmpty) s"; ${result.suppressedViolations.size} suppressed violation(s) in known non-compliant jar(s)"
        else ""
      log.info(s"JPS SDK subset bytecode OK: scanned ${result.scannedClasses} classes in ${result.scannedJars} jars, all (non-suppressed) bytecode <= Java $maxJava$suppressedSuffix.")
    }
  }

  def forkOptionsWithJBR: Def.Initialize[Task[ForkOptions]] = Def.task {
    val opts = (Test / forkOptions).value
    import org.jetbrains.sbtidea.download.jbr.JbrInstaller
    val jbrHome = JbrInstaller.getJbrHome(intellijBaseDirectory.value.toPath)
    opts.withJavaHome(jbrHome.toFile)
  }
}
