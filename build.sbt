import Common._
import Dependencies.sbtStructureExtractor
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.tasks.packaging.ShadePattern
import sbtide.Keys.ideSkipProject

// Global build settings

ideaPluginName in ThisBuild := "Scala"

ideaBuild in ThisBuild := Versions.ideaVersion

resolvers in ThisBuild ++=
  BintrayJetbrains.allResolvers :+
    Resolver.typesafeIvyRepo("releases") :+
    Resolver.sonatypeRepo("snapshots")

// Main projects
lazy val scalaCommunity: sbt.Project =
  newProject("scalaCommunity", file("."))
    .dependsOn(
      bsp % "test->test;compile->compile",
      codeInsight % "test->test;compile->compile",
      conversion % "test->test;compile->compile",
      worksheet % "test->test;compile->compile",
      scalaImpl % "test->test;compile->compile",
      devKitIntegration % "test->test;compile->compile",
      androidIntegration % "test->test;compile->compile",
      copyrightIntegration % "test->test;compile->compile",
      gradleIntegration % "test->test;compile->compile",
      intelliLangIntegration % "test->test;compile->compile",
      mavenIntegration % "test->test;compile->compile",
      propertiesIntegration % "test->test;compile->compile",
      javaDecompilerIntegration)
    .settings(
      ideExcludedDirectories    := Seq(baseDirectory.value / "target"),
      packageAdditionalProjects := Seq(scalaApi, compilerJps, repackagedZinc, decompiler, compilerShared, nailgunRunners, runners, sbtRuntimeDependencies),
      packageLibraryMappings    := Dependencies.scalaLibrary -> Some("lib/scala-library.jar") :: Nil,
      definedTests in Test := { // all sub-project tests need to be run within main project's classpath
        definedTests.all(ScopeFilter(inDependencies(scalaCommunity, includeRoot = false), inConfigurations(Test))).value.flatten }
    )

lazy val scalaApi = newProject(
  "scala-api",
  file("scala/scala-api")
)

lazy val codeInsight = newProject(
  "codeInsight",
  file("scala/codeInsight")
).dependsOn(
  scalaImpl % "test->test;compile->compile"
)

lazy val conversion = newProject(
  "conversion",
  file("scala/conversion")
).dependsOn(
  codeInsight % "test->test;compile->compile"
)

lazy val worksheet = newProject(
  "worksheet",
  file("scala/worksheet")
).dependsOn(
  scalaImpl % "test->test;compile->compile"
)

lazy val scalaImpl: sbt.Project =
  newProject("scala-impl", file("scala/scala-impl"))
    .dependsOn(
      compilerShared,
      scalaApi,
      macroAnnotations,
      decompiler % "test->test;compile->compile",
      runners    % "test->test;compile->compile")
    .enablePlugins(BuildInfoPlugin)
    .settings(
      ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects"),
      javacOptions in Global ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked"),
      scalacOptions in Global ++= Seq("-target:jvm-1.8", "-deprecation"),
      //scalacOptions in Global += "-Xmacro-settings:analyze-caches",
      libraryDependencies ++= DependencyGroups.scalaCommunity,
      addCompilerPlugin(Dependencies.macroParadise),
      ideaInternalPlugins := Seq(
        "IntelliLang",
        "java-i18n",
        "android",
        "smali",      // required by Android
        "gradle",     // required by Android
        "Groovy",     // required by Gradle
        "properties", // required by Gradle
        "maven",      // TODO remove after extracting the SBT module (which depends on Maven)
        "junit"
      ),
      ideaInternalPluginsJars :=
        ideaInternalPluginsJars.value.filterNot(cp => cp.data.getName.contains("junit-jupiter-api")),
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity),
      packageLibraryMappings ++= Seq(
        "org.scalameta" %% ".*" % ".*"                        -> Some("lib/scalameta.jar"),
        "com.trueaccord.scalapb" %% "scalapb-runtime" % ".*"  -> None,
        "com.google.protobuf" % "protobuf-java" % ".*"        -> None,
        "com.trueaccord.lenses" %% "lenses" % ".*"            -> None,
        "com.lihaoyi" %% "fastparse-utils" % ".*"             -> None,
        "commons-lang" % "commons-lang" % ".*"                -> None,
        Dependencies.scalaXml                                 -> Some("lib/scala-xml.jar"),
        Dependencies.scalaReflect                             -> Some("lib/scala-reflect.jar"),
        Dependencies.scalaLibrary                             -> None
      ),
      packageFileMappings ++= Seq(
        baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapperImpl.scala" -> "lib/jps/repl-interface-sources.jar",
        baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapper213Impl.scala" -> "lib/jps/repl-interface-sources.jar"
      ),
      buildInfoPackage := "org.jetbrains.plugins.scala.buildinfo",
      buildInfoKeys := Seq(
        name, version, scalaVersion, sbtVersion,
        BuildInfoKey.constant("sbtStructureVersion", Versions.sbtStructureVersion),
        BuildInfoKey.constant("sbtIdeaShellVersion", Versions.sbtIdeaShellVersion),
        BuildInfoKey.constant("sbtIdeaCompilerIndicesVersion", Versions.sbtIdeaCompilerIndicesVersion),
        BuildInfoKey.constant("sbtLatest_0_12", Versions.Sbt.latest_0_12),
        BuildInfoKey.constant("sbtLatest_0_13", Versions.Sbt.latest_0_13),
        BuildInfoKey.constant("sbtLatest_1_0", Versions.Sbt.latest_1_0),
        BuildInfoKey.constant("sbtLatestVersion", Versions.sbtVersion),
        BuildInfoKey.constant("sbtStructurePath_0_13",
          LocalRepoPackager.relativeJarPath013("sbt-structure-extractor", Versions.sbtStructureVersion)),
        BuildInfoKey.constant("sbtStructurePath_1_0",
          LocalRepoPackager.relativeJarPath1("sbt-structure-extractor", Versions.sbtStructureVersion))
      )
    )

lazy val compilerJps =
  newProject("compiler-jps", file("scala/compiler-jps"))
    .dependsOn(compilerShared, repackagedZinc)
    .settings(
      packageMethod           :=  PackagingMethod.Standalone("lib/jps/compiler-jps.jar", static = true),
      libraryDependencies     ++= Dependencies.nailgun :: Dependencies.zincInterface  :: Nil,
      packageLibraryMappings  ++= Dependencies.nailgun       -> Some("lib/jps/nailgun.jar") ::
                                  Dependencies.zincInterface -> Some("lib/jps/compiler-interface.jar") :: Nil)

lazy val repackagedZinc =
  newProject("repackagedZinc", file("target/tools/zinc"))
    .settings(
      packageOutputDir := baseDirectory.value / "plugin",
      packageAssembleLibraries := true,
      shadePatterns += ShadePattern("com.google.protobuf.**", "zinc.protobuf.@1"),
      packageMethod := PackagingMethod.DepsOnly("lib/jps/incremental-compiler.jar"),
      libraryDependencies += Dependencies.zinc)

lazy val compilerShared =
  newProject("compiler-shared", file("scala/compiler-shared"))
    .settings(
      libraryDependencies ++= Seq(Dependencies.nailgun, Dependencies.compilerIndicesProtocol),
      packageLibraryMappings ++= Seq(
        Dependencies.nailgun                 -> Some("lib/jps/nailgun.jar"),
        Dependencies.compilerIndicesProtocol -> Some("lib/scala-compiler-indices-protocol_2.12-0.1.1.jar")
      ),
      packageMethod := PackagingMethod.Standalone("lib/compiler-shared.jar", static = true)
    )

lazy val runners =
  newProject("runners", file("scala/runners"))
    .settings(
      packageMethod := PackagingMethod.Standalone(static = true),
      libraryDependencies ++= DependencyGroups.runners,
      // WORKAROUND fixes build error in sbt 0.13.12+ analogously to https://github.com/scala/scala/pull/5386/
      ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))
    )

lazy val nailgunRunners =
  newProject("nailgun", file("scala/nailgun"))
    .dependsOn(runners)
    .settings(
      libraryDependencies += Dependencies.nailgun,
      packageLibraryMappings += Dependencies.nailgun -> Some("lib/jps/nailgun.jar"),
      packageMethod := PackagingMethod.Standalone("lib/scala-nailgun-runner.jar", static = true)
    )

lazy val decompiler =
  newProject("decompiler", file("scala/decompiler"))
    .settings(
      libraryDependencies ++= DependencyGroups.decompiler,
      packageMethod := PackagingMethod.Standalone("lib/scalap.jar")
    )

lazy val macroAnnotations =
  newProject("macros", file("scala/macros"))
    .settings(
      addCompilerPlugin(Dependencies.macroParadise),
      libraryDependencies ++= Seq(Dependencies.scalaReflect, Dependencies.scalaCompiler),
      packageMethod        := PackagingMethod.Skip()
    )

lazy val bsp =
  newProject("bsp", file("bsp"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      libraryDependencies ++= DependencyGroups.bsp,
      ideaMainJars := Seq.empty
    )

// Integration with other IDEA plugins

lazy val devKitIntegration = newProject(
  "devKit",
  file("scala/integration/devKit")
).settings(
  ideaInternalPlugins := Seq("devkit")
)

lazy val androidIntegration =
  newProject("android", file("scala/integration/android"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      ideaInternalPlugins := Seq(
        "android",
        "smali", // required by Android
        "gradle", // required by Android
        "groovy", // required by Gradle
        "properties") // required by Gradle
    )

lazy val copyrightIntegration =
  newProject("copyright", file("scala/integration/copyright"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      ideaInternalPlugins := Seq("copyright")
    )

lazy val gradleIntegration =
  newProject("gradle", file("scala/integration/gradle"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      ideaInternalPlugins := Seq(
        "gradle",
        "groovy",     // required by Gradle
        "properties") // required by Gradle
    )

lazy val intelliLangIntegration = newProject(
  "intelliLang",
  file("scala/integration/intellilang")
).dependsOn(
  scalaImpl % "test->test;compile->compile"
).settings(
  ideaInternalPlugins := Seq("IntelliLang")
)

lazy val mavenIntegration =
  newProject("maven", file("scala/integration/maven"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      ideaInternalPlugins := Seq("maven")
    )

lazy val propertiesIntegration =
  newProject("properties", file("scala/integration/properties"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      ideaInternalPlugins := Seq("properties")
    )

lazy val javaDecompilerIntegration =
  newProject("java-decompiler", file("scala/integration/java-decompiler"))
    .dependsOn(scalaApi % Compile)
    .settings(
      ideaInternalPlugins := Seq("java-decompiler")
    )


// Utility projects

val localRepoArtifacts =
  (sbtStructureExtractor.name,  Versions.sbtStructureVersion) ::
  ("sbt-idea-shell",            Versions.sbtIdeaShellVersion) ::
  ("sbt-idea-compiler-indices", Versions.sbtIdeaCompilerIndicesVersion) :: Nil
val localRepoPaths = LocalRepoPackager.localPluginRepoPaths(localRepoArtifacts)

lazy val sbtRuntimeDependencies =
  (project in file("target/tools/sbt-runtime-dependencies"))
    .settings(
      libraryDependencies := DependencyGroups.sbtRuntime,
      managedScalaInstance := false,
      conflictManager := ConflictManager.all,
      conflictWarning := ConflictWarning.disable,
      resolvers += sbt.Classpaths.sbtPluginReleases,
      ideSkipProject := true,
      packageMethod := PackagingMethod.Skip(),
      packageLibraryMappings ++= Seq(
        Dependencies.sbtLaunch -> Some("launcher/sbt-launch.jar"),
        Dependencies.sbtInterface -> Some("lib/jps/sbt-interface.jar"),
        Dependencies.zincInterface -> Some("lib/jps/compiler-interface.jar"),
        Dependencies.compilerBridgeSources_2_13 -> Some("lib/jps/compiler-interface-sources-2.13.jar"),
        Dependencies.compilerBridgeSources_2_11 -> Some("lib/jps/compiler-interface-sources-2.11.jar"),
        Dependencies.compilerBridgeSources_2_10 -> Some("lib/jps/compiler-interface-sources-2.10.jar")
      ),
      update := {
        LocalRepoPackager.localPluginRepo(target.value / "repo", localRepoPaths)
        update.value
      },
      packageFileMappings ++= {
        val repoBase = target.value / "repo"
        localRepoPaths.map { path =>
          repoBase / path -> s"repo/$path"
        }
      }
    )

lazy val ideaRunner = createRunnerProject(scalaCommunity, "idea-runner")

//lazy val jmhBenchmarks =
//  newProject("benchmarks", file("scala/benchmarks"))
//    .dependsOn(scalaImpl % "test->test")
//    .enablePlugins(JmhPlugin)

// Testing keys and settings
import Common.TestCategory._

addCommandAlias("runPerfOptTests", s"testOnly -- --include-categories=$perfOptTests")
addCommandAlias("runSlowTests", s"testOnly -- --include-categories=$slowTests")
addCommandAlias("runDebuggerTests", s"testOnly -- --include-categories=$debuggerTests")
addCommandAlias("runHighlightingTests", s"testOnly -- --include-categories=$highlightingTests")
addCommandAlias("runScalacTests", s"testOnly -- --include-categories=$scalacTests")
addCommandAlias("runTypeInferenceTests", s"testOnly org.jetbrains.plugins.scala.lang.typeInference.*")

val fastTestOptions = "-v -s -a +c +q " +
  s"--exclude-categories=$slowTests " +
  s"--exclude-categories=$debuggerTests " +
  s"--exclude-categories=$perfOptTests " +
  s"--exclude-categories=$scalacTests " +
  s"--exclude-categories=$highlightingTests"

addCommandAlias("runFastTests", s"testOnly -- $fastTestOptions")
// subsets of tests to split the complete test run into smaller chunks
addCommandAlias("runFastTestsComIntelliJ", s"testOnly com.intellij.* -- $fastTestOptions")
addCommandAlias("runFastTestsOrgJetbrains", s"testOnly org.jetbrains.* -- $fastTestOptions")
addCommandAlias("runFastTestsScala", s"testOnly scala.* -- $fastTestOptions")

communityFullClasspath in ThisBuild :=
  deduplicatedClasspath(fullClasspath.in(scalaCommunity, Test).value, fullClasspath.in(scalaCommunity, Compile).value)

