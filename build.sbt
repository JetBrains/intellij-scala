import Common._
import Dependencies.sbtStructureExtractor
import org.jetbrains.sbtidea.Keys._
import sbtide.Keys.ideSkipProject

// Global build settings

intellijPluginName in ThisBuild := "Scala"

intellijBuild in ThisBuild := Versions.intellijVersion

intellijPlatform in ThisBuild := IntelliJPlatform.IdeaCommunity

resolvers in ThisBuild ++=
  BintrayJetbrains.allResolvers :+
    Resolver.typesafeIvyRepo("releases") :+
    Resolver.sonatypeRepo("snapshots")

javacOptions in Global := globalJavacOptions

scalacOptions in Global := globalScalacOptions

// Main projects
lazy val scalaCommunity: sbt.Project =
  newProject("scalaCommunity", file("."))
    .dependsOn(
      bsp % "test->test;compile->compile",
      codeInsight % "test->test;compile->compile",
      conversion % "test->test;compile->compile",
      uast % "test->test;compile->compile",
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
    .aggregate(tastyProvided, tastyCompile, tastyRuntime, tastyReader)
    .settings(
      ideExcludedDirectories    := Seq(baseDirectory.value / "target"),
      packageAdditionalProjects := Seq(scalaApi, compilerJps, repackagedZinc, decompiler, compilerShared, nailgunRunners, runners, runtimeDependencies, tastyCompile, tastyRuntime, tastyReader),
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

lazy val uast = newProject(
  "uast",
  file("scala/uast")
).dependsOn(
  scalaImpl % "test->test;compile->compile"
)

lazy val worksheet = newProject(
  "worksheet",
  file("scala/worksheet")
).dependsOn(
  scalaImpl % "test->test;compile->compile"
)

lazy val tastyProvided = newProject("tasty-provided", file("tasty/provided"))
  .settings(scalaVersion := "2.13.2", packageMethod := PackagingMethod.Skip())

lazy val tastyCompile = newProject("tasty-compile", file("tasty/compile"))
  .dependsOn(tastyProvided % Provided)
  .settings(scalaVersion := "2.13.2", packageMethod := PackagingMethod.Standalone("lib/tasty/tasty-compile.jar"))

lazy val tastyRuntime = newProject("tasty-runtime", file("tasty/runtime"))
  .dependsOn(tastyCompile % "compile-internal", tastyProvided % Provided)
  .settings(scalaVersion := "2.13.2", packageMethod := PackagingMethod.Standalone("lib/tasty/tasty-runtime.jar"))

lazy val tastyExample = newProject("tasty-example", file("tasty/example"))
  .dependsOn(tastyCompile, tastyProvided % Provided)
  .settings(scalaVersion := "2.13.2", libraryDependencies += "ch.epfl.lamp" % "dotty-library_0.26" % "0.26.0-RC1" % Runtime)

lazy val tastyReader = newProject("tasty-reader", file("tasty/reader"))
  .dependsOn(tastyCompile, tastyProvided % Provided)
  .settings(scalaVersion := "2.13.2", packageMethod := PackagingMethod.Standalone("lib/tasty/tasty-reader.jar"))

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
      //scalacOptions in Global += "-Xmacro-settings:analyze-caches",
      libraryDependencies ++= DependencyGroups.scalaCommunity,
//      addCompilerPlugin(Dependencies.macroParadise),
      intellijPlugins := Seq(
        "org.intellij.intelliLang",
        "com.intellij.java-i18n",
        "org.jetbrains.android",
        "com.intellij.stats.completion", // required for ml completion testing
        "com.android.tools.idea.smali",      // required by Android
        "com.intellij.gradle",     // required by Android
        "org.intellij.groovy",     // required by Gradle
        "org.jetbrains.idea.maven",      // TODO remove after extracting the SBT module (which depends on Maven)
        "JUnit"
      ).map(_.toPlugin),
      intellijPluginJars :=
        intellijPluginJars.value.filterNot(cp => cp.data.getName.contains("junit-jupiter-api")),
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity),
      packageLibraryMappings ++= Seq(
        "org.scalameta" %% ".*" % ".*"                        -> Some("lib/scalameta.jar"),
        "com.thesamet.scalapb" %% "scalapb-runtime" % ".*"  -> None,
        "com.thesamet.scalapb" %% "lenses" % ".*"            -> None,
        Dependencies.scalaXml                                 -> Some("lib/scala-xml.jar"),
        Dependencies.scalaReflect                             -> Some("lib/scala-reflect.jar"),
        Dependencies.scalaLibrary                             -> None
      ),
      packageFileMappings ++= Seq(
        baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapperImpl.scala"      -> "lib/jps/repl-interface-sources.jar",
        baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapper213_0Impl.scala" -> "lib/jps/repl-interface-sources.jar",
        baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapper213Impl.scala"   -> "lib/jps/repl-interface-sources.jar",
        baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapper3Impl.scala"     -> "lib/jps/repl-interface-sources.jar"
      ),
      buildInfoPackage := "org.jetbrains.plugins.scala.buildinfo",
      buildInfoKeys := Seq(
        name, version, scalaVersion, sbtVersion,
        BuildInfoKey.constant("bloopVersion", Versions.bloopVersion),
        BuildInfoKey.constant("sbtStructureVersion", Versions.sbtStructureVersion),
        BuildInfoKey.constant("sbtIdeaShellVersion", Versions.sbtIdeaShellVersion),
        BuildInfoKey.constant("sbtIdeaCompilerIndicesVersion", Versions.compilerIndicesVersion),
        BuildInfoKey.constant("sbtLatest_0_12", Versions.Sbt.latest_0_12),
        BuildInfoKey.constant("sbtLatest_0_13", Versions.Sbt.latest_0_13),
        BuildInfoKey.constant("sbtLatest_1_0", Versions.Sbt.latest_1_0),
        BuildInfoKey.constant("sbtLatestVersion", Versions.sbtVersion),
        BuildInfoKey.constant("sbtStructurePath_0_13",
          LocalRepoPackager.relativeJarPath013("org.jetbrains", "sbt-structure-extractor", Versions.sbtStructureVersion)),
        BuildInfoKey.constant("sbtStructurePath_1_0",
          LocalRepoPackager.relativeJarPath1("org.jetbrains", "sbt-structure-extractor", Versions.sbtStructureVersion))
      )
    )

lazy val compilerJps =
  newProject("compiler-jps", file("scala/compiler-jps"))
    .dependsOn(compilerShared, repackagedZinc)
    .settings(
      packageMethod           :=  PackagingMethod.Standalone("lib/jps/compiler-jps.jar", static = true),
      libraryDependencies     ++= Dependencies.nailgun :: Dependencies.zincInterface :: Dependencies.scalaParallelCollections :: Nil,
      packageLibraryMappings  ++= Dependencies.nailgun       -> Some("lib/jps/nailgun.jar") ::
                                  Dependencies.zincInterface -> Some("lib/jps/compiler-interface.jar") ::
                                  Dependencies.scalaParallelCollections -> Some("lib/jps/scala-parallel-collections.jar") :: Nil
    )

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
      libraryDependencies ++= Seq(Dependencies.nailgun, Dependencies.compilerIndicesProtocol, Dependencies.zincInterface),
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
      libraryDependencies ++= DependencyGroups.runners
      // WORKAROUND fixes build error in sbt 0.13.12+ analogously to https://github.com/scala/scala/pull/5386/
//      ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))
    )

lazy val nailgunRunners =
  newProject("nailgun", file("scala/nailgun"))
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
//      addCompilerPlugin(Dependencies.macroParadise),
      libraryDependencies ++= Seq(Dependencies.scalaReflect, Dependencies.scalaCompiler),
      packageMethod        := PackagingMethod.Skip()
    )

lazy val bsp =
  newProject("bsp", file("bsp"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      libraryDependencies ++= DependencyGroups.bsp,
      intellijMainJars := Seq.empty
    )

// Integration with other IDEA plugins

lazy val devKitIntegration = newProject(
  "devKit",
  file("scala/integration/devKit"))
  .dependsOn(scalaImpl)
  .settings(
  intellijPlugins += "DevKit".toPlugin
)

lazy val androidIntegration =
  newProject("android", file("scala/integration/android"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      intellijPlugins ++= Seq(
        "org.jetbrains.android",
        "com.android.tools.idea.smali",      // required by Android
        "com.intellij.gradle",     // required by Android
        "org.intellij.groovy",     // required by Gradle
        "com.intellij.properties").map(_.toPlugin) // required by Gradle
    )

lazy val copyrightIntegration =
  newProject("copyright", file("scala/integration/copyright"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      intellijPlugins += "com.intellij.copyright".toPlugin
    )

lazy val gradleIntegration =
  newProject("gradle", file("scala/integration/gradle"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      intellijPlugins ++= Seq(
        "com.intellij.gradle",     // required by Android
        "org.intellij.groovy",     // required by Gradle
        "com.intellij.properties").map(_.toPlugin) // required by Gradle
    )

lazy val intelliLangIntegration = newProject(
  "intelliLang",
  file("scala/integration/intellilang")
).dependsOn(
  scalaImpl % "test->test;compile->compile"
).settings(
//  addCompilerPlugin(Dependencies.macroParadise),
  intellijPlugins += "org.intellij.intelliLang".toPlugin
)

lazy val mavenIntegration =
  newProject("maven", file("scala/integration/maven"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      intellijPlugins += "org.jetbrains.idea.maven".toPlugin
    )

lazy val propertiesIntegration =
  newProject("properties", file("scala/integration/properties"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      intellijPlugins += "com.intellij.properties".toPlugin
    )

lazy val javaDecompilerIntegration =
  newProject("java-decompiler", file("scala/integration/java-decompiler"))
    .dependsOn(scalaApi % Compile)
    .settings(
      intellijPlugins += "org.jetbrains.java.decompiler".toPlugin
    )


// Utility projects

val localRepoArtifacts =
  ("org.jetbrains", sbtStructureExtractor.name,  Versions.sbtStructureVersion) ::
  ("org.jetbrains", "sbt-idea-shell",            Versions.sbtIdeaShellVersion) ::
  ("org.jetbrains.scala" ,"sbt-idea-compiler-indices", Versions.compilerIndicesVersion) :: Nil
val localRepoPaths = LocalRepoPackager.localPluginRepoPaths(localRepoArtifacts)

lazy val runtimeDependencies =
  (project in file("target/tools/runtime-dependencies"))
    .settings(
      scalaVersion := Versions.scalaVersion,
      libraryDependencies := DependencyGroups.runtime,
      managedScalaInstance := true,
      conflictManager := ConflictManager.all,
      conflictWarning := ConflictWarning.disable,
      resolvers += sbt.Classpaths.sbtPluginReleases,
      ideSkipProject := true,
      packageMethod := PackagingMethod.DepsOnly(),
      packageLibraryMappings ++= Seq(
        Dependencies.bloopLauncher -> Some("launcher/bloop-launcher.jar"),
        Dependencies.sbtLaunch -> Some("launcher/sbt-launch.jar"),
        Dependencies.sbtInterface -> Some("lib/jps/sbt-interface.jar"),
        Dependencies.zincInterface -> Some("lib/jps/compiler-interface.jar"),
        Dependencies.dottySbtBridge -> Some("lib/jps/dotty-sbt-bridge.jar"),
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
addCommandAlias("runTypeInferenceTests", s"testOnly -- --include-categories=$typecheckerTests")
addCommandAlias("runTestingSupportTests", s"testOnly -- --include-categories=$testingSupportTests")
addCommandAlias("runWorksheetEvaluationTests", s"testOnly -- --include-categories=$worksheetEvaluationTests")
addCommandAlias("runFlakyTests", s"testOnly -- --include-categories=$flakyTests")

val fastTestOptions = "-v -s -a +c +q " +
  s"--exclude-categories=$slowTests " +
  s"--exclude-categories=$debuggerTests " +
  s"--exclude-categories=$perfOptTests " +
  s"--exclude-categories=$scalacTests " +
  s"--exclude-categories=$typecheckerTests " +
  s"--exclude-categories=$testingSupportTests " +
  s"--exclude-categories=$highlightingTests " +
  s"--exclude-categories=$worksheetEvaluationTests " +
  s"--exclude-categories=$flakyTests "

addCommandAlias("runFastTests", s"testOnly -- $fastTestOptions")
// subsets of tests to split the complete test run into smaller chunks
addCommandAlias("runFastTestsComIntelliJ", s"testOnly com.intellij.* -- $fastTestOptions")
addCommandAlias("runFastTestsOrgJetbrains", s"testOnly org.jetbrains.* -- $fastTestOptions")
addCommandAlias("runFastTestsScala", s"testOnly scala.* -- $fastTestOptions")

communityFullClasspath in ThisBuild :=
  deduplicatedClasspath(fullClasspath.in(scalaCommunity, Test).value, fullClasspath.in(scalaCommunity, Compile).value)

