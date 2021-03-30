import Common._
import Dependencies.{provided, sbtStructureExtractor}
import sbtide.Keys.ideSkipProject

// Global build settings

intellijPluginName in ThisBuild := "Scala"

intellijBuild in ThisBuild := Versions.intellijVersion

intellijPlatform in ThisBuild := intellijPlatform.in(Global).??(IntelliJPlatform.IdeaCommunity).value

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
      dfa % "test->test;compile->compile",
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
      javaDecompilerIntegration,
      mlCompletionIntegration % "test->test;compile->compile"
    )
    .settings(
      ideExcludedDirectories    := Seq(baseDirectory.value / "target"),
      packageAdditionalProjects := Seq(
        scalaApi,
        compilerJps,
        /*worksheetReplReporters,*/
        repackagedZinc,
        decompiler,
        compilerShared,
        nailgunRunners,
        runners,
        runtimeDependencies,
      ),
      packageLibraryMappings    := Dependencies.scalaLibrary -> Some("lib/scala-library.jar") :: Nil,
      intellijPlugins := intellijPlugins.all(ScopeFilter(inDependencies(ThisProject, includeRoot = false))).value.flatten.distinct,
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

lazy val dfa = newProject(
  "dfa",
  file("scala/dfa")
).settings(
  testFrameworks in Test += TestFrameworks.ScalaTest,
  libraryDependencies ++= DependencyGroups.dfa,
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint",
    "-Xfatal-warnings"
  ),
  // the internet says this is smart thing to do
  scalacOptions in (Compile, console) ~= {
    _.filterNot(Set("-Xlint"))
  }
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

lazy val worksheet =
  newProject("worksheet", file("scala/worksheet"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      worksheetReplInterface % "test->test;compile->compile"
    )

lazy val worksheetReplInterface =
  newProject("worksheet-repl-interface", file("scala/worksheet-repl-interface"))
    .settings(
      javacOptions  in Compile := outOfIDEAProcessJavacOptions,
      scalacOptions in Compile := outOfIDEAProcessScalacOptions,
      packageMethod :=  PackagingMethod.Standalone("lib/repl-interface.jar", static = true)
    )

lazy val tastyRuntime = Project("tasty-runtime", file("tasty/runtime"))
  .settings(scalaVersion := "3.0.0-RC2",
    intellijMainJars := Seq.empty,
    libraryDependencies += "org.scala-lang" % "scala3-tasty-inspector_3.0.0-RC2" % "3.0.0-RC2" excludeAll(
      ExclusionRule(organization = "org.scala-lang.modules"),
      ExclusionRule(organization = "org.scala-sbt"),
      ExclusionRule(organization = "org.jline"),
      ExclusionRule(organization = "net.java.dev.jna"),
      ExclusionRule(organization = "com.google.protobuf")
    ),
    scalacOptions in Compile := Seq("-strict"), // TODO If there are no unique options, sbt import adds the module to a profile with macros enabled.
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
    packageMethod := PackagingMethod.Standalone("lib/tasty/tasty-runtime.jar"),
    packageLibraryBaseDir := file("lib/tasty/"),
    // TODO Use scala3-library in lib/ (when there will be one)
    packageLibraryMappings := Seq(
      "org.scala-lang" %% "scala-library" % ".*" -> None,
    ),
  )

lazy val scalaImpl: sbt.Project =
  newProject("scala-impl", file("scala/scala-impl"))
    .dependsOn(
      compilerShared,
      scalaApi,
      macroAnnotations,
      decompiler % "test->test;compile->compile",
      runners % "test->test;compile->compile",
      testRunners % "test->test;compile->compile",
    )
    .dependsOn(scalatestFinders % "compile->compile")
    // scala-test-finders use different scala versions, so do not depend on it, just aggregate the tests
    .aggregate(scalatestFindersTests.map(sbt.Project.projectToLocalProject): _*)
    .aggregate(tastyRuntime)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects"),
      //scalacOptions in Global += "-Xmacro-settings:analyze-caches",
      libraryDependencies ++= DependencyGroups.scalaCommunity,
      intellijPlugins ++= Seq(
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
        intellijPluginJars.value.map { case (descriptor, cp) => descriptor -> cp.filterNot(_.data.getName.contains("junit-jupiter-api")) },
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity),
      packageAdditionalProjects := Seq(tastyRuntime),
      packageLibraryMappings ++= Seq(
        "org.scalameta" %% ".*" % ".*"                        -> Some("lib/scalameta.jar"),
        "com.thesamet.scalapb" %% "scalapb-runtime" % ".*"  -> None,
        "com.thesamet.scalapb" %% "lenses" % ".*"            -> None,
        Dependencies.scalaXml                                 -> Some("lib/scala-xml.jar"),
        Dependencies.scalaReflect                             -> Some("lib/scala-reflect.jar"),
        Dependencies.scalaLibrary                             -> None
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
    .dependsOn(compilerShared, repackagedZinc, worksheetReplInterface)
    .settings(
      javacOptions  in Compile := outOfIDEAProcessJavacOptions,
      scalacOptions in Compile := outOfIDEAProcessScalacOptions,
      packageMethod           :=  PackagingMethod.Standalone("lib/jps/compiler-jps.jar", static = true),
      libraryDependencies     ++= Seq(Dependencies.nailgun,
                                      Dependencies.zincInterface,
                                      Dependencies.scalaParallelCollections),
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
      javacOptions  in Compile := outOfIDEAProcessJavacOptions,
      scalacOptions in Compile := outOfIDEAProcessScalacOptions,
      libraryDependencies ++= Seq(Dependencies.nailgun, Dependencies.compilerIndicesProtocol, Dependencies.zincInterface),
      packageLibraryMappings ++= Seq(
        Dependencies.nailgun                 -> Some("lib/jps/nailgun.jar"),
        Dependencies.compilerIndicesProtocol -> Some("lib/scala-compiler-indices-protocol_2.12-0.1.1.jar")
      ),
      packageMethod := PackagingMethod.Standalone("lib/compiler-shared.jar", static = true)
    )

lazy val runners: Project =
  newProject("runners", file("scala/runners"))
    .settings(
      javacOptions  in Compile := outOfIDEAProcessJavacOptions,
      scalacOptions in Compile := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone(static = true),
      packageAdditionalProjects ++= Seq(testRunners, testRunners_spec2_2x)
    )

lazy val testRunners: Project =
  newProject("testRunners", file("scala/testRunners"))
    .settings(
      javacOptions  in Compile := outOfIDEAProcessJavacOptions,
      scalacOptions in Compile := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.MergeIntoOther(runners),
      libraryDependencies ++= DependencyGroups.testRunners
    )

lazy val testRunners_spec2_2x: Project =
  newProject("testRunners_spec2_2x", file("scala/testRunners_spec2_2x"))
    .dependsOn(testRunners)
    .settings(
      javacOptions  in Compile := outOfIDEAProcessJavacOptions,
      scalacOptions in Compile := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.MergeIntoOther(runners),
      libraryDependencies ++= Seq(provided.specs2_2x)
    )

lazy val scalatestFindersRootDir = file("scala/test-integration/scalatest-finders")

lazy val scalatestFinders = Project("scalatest-finders", scalatestFindersRootDir)
  .settings(
    // NOTE: we might continue NOT using Scala in scalatestFinders just in case
    // in some future we will decide again to extract the library, so as it can be used even without scala jar
    crossPaths := false, // disable using the Scala version in output paths and artifacts
    autoScalaLibrary := false, // removes Scala dependency,
    javacOptions := Seq("--release", "11"), // finders are run in IDEA process, so using JDK 11
    packageMethod := PackagingMethod.Standalone("lib/scalatest-finders-patched.jar")
  )

lazy val scalatestFindersTests: Seq[Project] = Seq(
  scalatestFindersTests_2,
  scalatestFindersTests_3_0,
  scalatestFindersTests_3_2
)

lazy val scalatestFindersTestSettings = Seq(
  scalacOptions := Seq("-deprecation")
)
val scalatestLatest_2   = "2.2.6"
val scalatestLatest_3_0 = "3.0.9"
val scalatestLatest_3_2 = "3.2.5"

lazy val scalatestFindersTests_2 = Project("scalatest-finders-tests-2", scalatestFindersRootDir / "tests-2")
  .dependsOn(scalatestFinders)
  .settings(
    scalatestFindersTestSettings,
    scalaVersion := "2.11.12",
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_2 % Test)
  )
lazy val scalatestFindersTests_3_0 = Project("scalatest-finders-tests-3_0", scalatestFindersRootDir / "tests-3_0")
  .dependsOn(scalatestFinders)
  .settings(
    scalatestFindersTestSettings,
    scalaVersion := "2.12.13",
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_3_0 % Test)
  )
lazy val scalatestFindersTests_3_2 = Project("scalatest-finders-tests-3_2", scalatestFindersRootDir / "tests-3_2")
  .dependsOn(scalatestFinders)
  .settings(
    scalatestFindersTestSettings,
    scalaVersion := "2.13.4",
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_3_2 % Test)
  )

lazy val nailgunRunners =
  newProject("nailgun", file("scala/nailgun"))
    .settings(
      javacOptions  in Compile := outOfIDEAProcessJavacOptions,
      scalacOptions in Compile := outOfIDEAProcessScalacOptions,
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
      libraryDependencies ++= Seq(Dependencies.scalaReflect, Dependencies.scalaCompiler),
      packageMethod        := PackagingMethod.Skip()
    )

lazy val bsp =
  newProject("bsp", file("bsp"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      worksheet % "test->test;compile->compile"
    )
    .settings(
      libraryDependencies ++= DependencyGroups.bsp,
      intellijPlugins += "JUnit".toPlugin,
//      intellijMainJars := Seq.empty // why was this unset?
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

lazy val mlCompletionIntegration =
  newProject("ml-completion", file("scala/integration/ml-completion"))
    .dependsOn(scalaImpl)
    .settings(
      intellijPlugins += "com.intellij.completion.ml.ranking".toPlugin,
      resolvers += Resolver.bintrayRepo("jetbrains", "intellij-third-party-dependencies"),
      libraryDependencies += "org.jetbrains.intellij.deps.completion" % "completion-ranking-scala" % "0.3.2"
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
      packageLibraryMappings := Seq(
        "org.scala-lang.modules" % "scala-.*" % ".*" -> None,
        Dependencies.sbtLaunch -> Some("launcher/sbt-launch.jar"),
        Dependencies.sbtInterface -> Some("lib/jps/sbt-interface.jar"),
        Dependencies.zincInterface -> Some("lib/jps/compiler-interface.jar"),
        Dependencies.dottySbtBridge -> Some("lib/jps/dotty-sbt-bridge.jar"),
        Dependencies.scala3SbtBridge -> Some("lib/jps/scala3-sbt-bridge.jar"),
        Dependencies.compilerBridgeSources_2_13 -> Some("lib/jps/compiler-interface-sources-2.13.jar"),
        Dependencies.compilerBridgeSources_2_11 -> Some("lib/jps/compiler-interface-sources-2.11.jar"),
        Dependencies.compilerBridgeSources_2_10 -> Some("lib/jps/compiler-interface-sources-2.10.jar"),
      ),
      update := {
        LocalRepoPackager.localPluginRepo(
          target.value / "repo",
          localRepoPaths,
          (ThisBuild/baseDirectory).value / "project" / "resources")
        update.value
      },
      packageFileMappings ++= {
        val repoBase = target.value / "repo"
        localRepoPaths.map { path =>
          repoBase / path -> s"repo/$path"
        }
      }
    )

//lazy val jmhBenchmarks =
//  newProject("benchmarks", file("scala/benchmarks"))
//    .dependsOn(scalaImpl % "test->test")
//    .enablePlugins(JmhPlugin)

// Testing keys and settings
import Common.TestCategory._

def testOnlyCategory(category: String): String =
  s"testOnly -- --include-categories=$category --exclude-categories=$flakyTests"

addCommandAlias("runPerfOptTests", testOnlyCategory(perfOptTests))
addCommandAlias("runSlowTests", testOnlyCategory(slowTests))
addCommandAlias("runDebuggerTests", testOnlyCategory(debuggerTests))
addCommandAlias("runHighlightingTests", testOnlyCategory(highlightingTests))
addCommandAlias("runScalacTests", testOnlyCategory(scalacTests))
addCommandAlias("runTypeInferenceTests", testOnlyCategory(typecheckerTests))
addCommandAlias("runTestingSupportTests", testOnlyCategory(testingSupportTests))
addCommandAlias("runWorksheetEvaluationTests", testOnlyCategory(worksheetEvaluationTests))
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

// run dfa tests directly in that module
addCommandAlias("runDfaTests", "dfa/test")

communityFullClasspath in ThisBuild :=
  deduplicatedClasspath(fullClasspath.in(scalaCommunity, Test).value, fullClasspath.in(scalaCommunity, Compile).value)

