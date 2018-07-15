import Common._
import sbtide.Keys.ideSkipProject

// Global build settings

ideaPluginName in ThisBuild := "Scala"

ideaBuild in ThisBuild := Versions.ideaVersion

resolvers in ThisBuild ++=
  BintrayJetbrains.allResolvers :+
    Resolver.typesafeIvyRepo("releases")

resolvers in ThisBuild += Resolver.sonatypeRepo("snapshots")

// Main projects
lazy val scalaCommunity: sbt.Project =
  newProject("scalaCommunity", file("."))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
//      bsp % "test->test;compile->compile",
      androidIntegration % "test->test;compile->compile",
      copyrightIntegration % "test->test;compile->compile",
      gradleIntegration % "test->test;compile->compile",
      intellilangIntegration % "test->test;compile->compile",
      mavenIntegration % "test->test;compile->compile",
      propertiesIntegration % "test->test;compile->compile")
    .aggregate(
      scalaImpl,
//      bsp,
      androidIntegration,
      copyrightIntegration,
      gradleIntegration,
      intellilangIntegration,
      mavenIntegration,
      propertiesIntegration)
    .settings(
      ideExcludedDirectories    := Seq(baseDirectory.value / "target"),
      packageAdditionalProjects := Seq(compilerJps, repackagedZinc, decompiler, compilerShared, nailgunRunners, runners, sbtRuntimeDependencies),
      packageLibraryMappings    := Dependencies.scalaLibrary -> Some("lib/scala-library.jar") :: Nil )

lazy val scalaImpl: sbt.Project =
  newProject("scala-impl", file("scala/scala-impl"))
    .dependsOn(compilerShared, decompiler % "test->test;compile->compile", runners % "test->test;compile->compile", macroAnnotations)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects"),
      javacOptions in Global ++= Seq("-source", "1.8", "-target", "1.8"),
      scalacOptions in Global ++= Seq("-target:jvm-1.8", "-deprecation"),
      //scalacOptions in Global += "-Xmacro-settings:analyze-caches",
      libraryDependencies ++= DependencyGroups.scalaCommunity,
      addCompilerPlugin(Dependencies.macroParadise),
      ideaInternalPlugins := Seq(
        "IntelliLang",
        "java-i18n",
        "android",
        "smali", // required by Android
        "gradle", // requierd by Android
        "Groovy", // requierd by Gradle
        "properties", // required by Gradle
        "maven", // TODO remove after extracting the SBT module (which depends on Maven)
        "junit"
      ),
      ideaInternalPluginsJars :=
        ideaInternalPluginsJars.value.filterNot(cp => cp.data.getName.contains("junit-jupiter-api")),
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity),
      packageLibraryMappings ++= Seq(
        "org.scalameta" %% ".*" % ".*"                        -> Some("lib/scalameta.jar"),
        "com.trueaccord.scalapb" %% "scalapb-runtime" % ".*"  -> None,
        "com.trueaccord.lenses" %% "lenses" % ".*"            -> None,
        "com.lihaoyi" %% "sourcecode" % ".*"                  -> None,
        "com.lihaoyi" %% "fastparse-utils" % ".*"             -> None,
        "com.typesafe" % "config" % ".*"                      -> None,
        "commons-lang" % "commons-lang" % ".*"                -> None,
        Dependencies.scalaXml                                 -> Some("lib/scala-xml.jar"),
        Dependencies.scalaReflect                             -> Some("lib/scala-reflect.jar"),
        Dependencies.scalaParserCombinators                   -> Some("lib/scala-parser-combinators.jar"),
        Dependencies.scalaLibrary                             -> None
      ),
      packageFileMappings += baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapperImpl.scala" ->
                            "lib/jps/repl-interface-sources.jar",
      buildInfoPackage := "org.jetbrains.plugins.scala.buildinfo",
      buildInfoKeys := Seq(
        name, version, scalaVersion, sbtVersion,
        BuildInfoKey.constant("sbtLatestVersion", Versions.sbtVersion),
        BuildInfoKey.constant("sbtStructureVersion", Versions.sbtStructureVersion),
        BuildInfoKey.constant("sbtIdeaShellVersion", Versions.sbtIdeaShellVersion),
        BuildInfoKey.constant("sbtLatest_0_13", Versions.Sbt.latest_0_13)
      )
    )

lazy val compilerJps =
  newProject("compiler-jps", file("scala/compiler-jps"))
    .dependsOn(compilerShared, repackagedZinc)
    .settings(
      packageMethod           :=  PackagingMethod.Standalone("lib/jps/compiler-jps.jar"),
      libraryDependencies     ++= Dependencies.nailgun :: Dependencies.zincInterface  :: Nil,
      packageLibraryMappings  ++= Dependencies.nailgun       -> Some("lib/jps/nailgun.jar") ::
                                  Dependencies.zincInterface -> Some("lib/jps/compiler-interface.jar") :: Nil)

lazy val repackagedZinc =
  newProject("repackagedZinc", file("target/tools/zinc"))
    .settings(
      packageOutputDir := baseDirectory.value / "plugin",
      packageAssembleLibraries := true,
      packageMethod := PackagingMethod.Standalone("lib/jps/incremental-compiler.jar"),
      libraryDependencies += Dependencies.zinc,
      ideSkipProject := true)

lazy val compilerShared =
  newProject("compiler-shared", file("scala/compiler-shared"))
    .settings(
      libraryDependencies += Dependencies.nailgun,
      packageLibraryMappings += Dependencies.nailgun -> Some("lib/jps/nailgun.jar"),
      packageMethod := PackagingMethod.Standalone("lib/compiler-shared.jar")
    )

lazy val runners =
  newProject("runners", file("scala/runners"))
    .settings(
      packageMethod := PackagingMethod.Standalone(),
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
      packageMethod := PackagingMethod.Standalone("lib/scala-nailgun-runners.jar")
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
  newProject("bsp", file("scala/bsp"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      libraryDependencies ++= DependencyGroups.bsp,
      packageAssembleLibraries := true,
      packageMethod := PackagingMethod.Standalone("lib/bsp.jar")
  )

// Integration with other IDEA plugins

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

lazy val intellilangIntegration =
  newProject("intellilang", file("scala/integration/intellilang"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
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


// Utility projects

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
        Dependencies.sbtLaunch                  -> Some("launcher/sbt-launch.jar"),
        Dependencies.sbtInterface               -> Some("lib/jps/sbt-interface.jar"),
        Dependencies.zincInterface              -> Some("lib/jps/compiler-interface.jar"),
        Dependencies.compilerBridgeSources_2_13 -> Some("lib/jps/compiler-interface-sources-2.13.jar"),
        Dependencies.compilerBridgeSources_2_11 -> Some("lib/jps/compiler-interface-sources-2.11.jar"),
        Dependencies.compilerBridgeSources_2_10 -> Some("lib/jps/compiler-interface-sources-2.10.jar"),
        Dependencies.sbtStructureExtractor_100  -> Some("launcher/sbt-structure-1.0.jar"),
        Dependencies.sbtStructureExtractor_013  -> Some("launcher/sbt-structure-0.13.jar"),
        Dependencies.sbtStructureExtractor_012  -> Some("launcher/sbt-structure-0.12.jar"),
        "org.scala-sbt" % "util-interface" % "1.1.2" -> None,
        "org.scala-sbt" % "launcher" % "1.0.3"       -> None
      ),
      update := {
        import Dependencies._
        LocalRepoPackager.localPluginRepo(
          target.value / "repo",
            (sbtStructureExtractor.name,  Versions.sbtStructureVersion) ::
            ("sbt-idea-shell",            Versions.sbtIdeaShellVersion) :: Nil)
        update.value
      },
      packageFileMappings += target.value / "repo" -> "repo/" )

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

