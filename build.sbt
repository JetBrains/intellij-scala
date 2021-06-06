import Common._
import Dependencies.provided
import LocalRepoPackager.{localRepoDependencies, localRepoUpdate, relativeJarPath, sbtDep}
import org.jetbrains.sbtidea.Keys._
import sbtide.Keys.ideSkipProject

import java.nio.file.Paths

// Global build settings

(ThisBuild / intellijPluginName) := "Scala"

(ThisBuild / intellijBuild) := Versions.intellijVersion

(ThisBuild / intellijPlatform) := (Global / intellijPlatform).??(IntelliJPlatform.IdeaCommunity).value

 (ThisBuild / resolvers) ++= Seq(
   Resolver.sonatypeRepo("releases"),
   Resolver.sonatypeRepo("staging"),
   Resolver.sonatypeRepo("snapshots"),
 )

(Global / javacOptions) := globalJavacOptions

(Global / scalacOptions) := globalScalacOptions

// Main projects
lazy val scalaCommunity: sbt.Project =
  newProject("scalaCommunity", file("."))
    .dependsOn(
      bsp % "test->test;compile->compile",
      codeInsight % "test->test;compile->compile",
      dfa % "test->test;compile->compile",
      traceLogger % "test->test;compile->compile",
      traceLogViewer % "test->test;compile->compile",
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
      packageSearchIntegration,
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
      (Test / definedTests) := { // all sub-project tests need to be run within main project's classpath
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
  (Test / testFrameworks) += TestFrameworks.ScalaTest,
  libraryDependencies ++= DependencyGroups.dfa,
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint",
    "-Xfatal-warnings"
  ),
  // the internet says this is smart thing to do
  (Compile / console / scalacOptions) ~= {
    _.filterNot(Set("-Xlint"))
  }
)

lazy val traceLogger = newProject(
  "traceLogger",
  file("scala/traceLogger")
).settings(
  libraryDependencies ++= DependencyGroups.traceLogger,
  libraryDependencies ++= Seq(Dependencies.scalaReflect, Dependencies.scalaCompiler),
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint",
    "-Xfatal-warnings"
  ),
  // the internet says this is smart thing to do
  (Compile / console / scalacOptions) ~= {
    _.filterNot(Set("-Xlint"))
  }
)

lazy val traceLogViewer = newProject(
  "traceLogViewer",
  file("scala/traceLogViewer")
).dependsOn(
  traceLogger,
  scalaImpl % "test->test;compile->compile"
).settings(
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint",
    "-Xfatal-warnings"
  ),
  // the internet says this is smart thing to do
  (Compile / console / scalacOptions) ~= {
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
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod :=  PackagingMethod.Standalone("lib/repl-interface.jar", static = true)
    )

lazy val tastyRuntime = Project("tasty-runtime", file("tasty/runtime"))
  .settings(
    intellijMainJars := Seq.empty,
    scalaVersion := "3.0.0",
    libraryDependencies += "org.scala-lang" % "scala3-tasty-inspector_3" % "3.0.0" excludeAll(
      ExclusionRule(organization = "org.scala-lang.modules"),
      ExclusionRule(organization = "org.scala-sbt"),
      ExclusionRule(organization = "org.jline"),
      ExclusionRule(organization = "net.java.dev.jna"),
      ExclusionRule(organization = "com.google.protobuf")
    ),
    (Compile / scalacOptions) := Seq("-strict"), // TODO If there are no unique options, sbt import adds the module to a profile with macros enabled.
    (Compile / unmanagedSourceDirectories) += baseDirectory.value / "src",
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
      traceLogger,
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
        //"com.intellij.stats.completion", // required for ml completion testing (plugin manager returns 404 for it)
        "com.android.tools.idea.smali",      // required by Android
        "com.intellij.gradle",     // required by Android
        "org.intellij.groovy",     // required by Gradle
        "org.jetbrains.idea.maven",      // TODO remove after extracting the SBT module (which depends on Maven)
        "com.jetbrains.packagesearch.intellij-plugin",
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
          relativeJarPath(sbtDep("org.jetbrains.scala","sbt-structure-extractor", Versions.sbtStructureVersion, "0.13"))),
        BuildInfoKey.constant("sbtStructurePath_1_0", 
          relativeJarPath(sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, "1.0")))
        )
    )

val nailgunJar = settingKey[File]("location of nailgun jar").withRank(KeyRanks.Invisible)
(ThisBuild / nailgunJar) := (scalaCommunity / unmanagedBase).value / "nailgun.jar"

lazy val compilerJps =
  newProject("compiler-jps", file("scala/compiler-jps"))
    .dependsOn(compilerShared, repackagedZinc, worksheetReplInterface)
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod            :=  PackagingMethod.Standalone("lib/jps/compiler-jps.jar", static = true),
      Compile/unmanagedJars    += nailgunJar.value,
      libraryDependencies      ++= Seq(Dependencies.zincInterface, Dependencies.scalaParallelCollections),
      packageLibraryMappings   ++= Seq(
        Dependencies.zincInterface -> Some("lib/jps/compiler-interface.jar"),
        Dependencies.scalaParallelCollections -> Some("lib/jps/scala-parallel-collections.jar")
      )
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
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      Compile/unmanagedJars += nailgunJar.value,
      libraryDependencies ++= Seq(Dependencies.compilerIndicesProtocol, Dependencies.zincInterface),
      packageLibraryMappings ++= Seq(
        Dependencies.compilerIndicesProtocol -> Some("lib/scala-compiler-indices-protocol_2.12-0.1.1.jar")
      ),
      packageMethod := PackagingMethod.Standalone("lib/compiler-shared.jar", static = true)
    )

lazy val runners: Project =
  newProject("runners", file("scala/runners"))
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone(static = true),
      packageAdditionalProjects ++= Seq(testRunners, testRunners_spec2_2x)
    )

lazy val testRunners: Project =
  newProject("testRunners", file("scala/testRunners"))
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.MergeIntoOther(runners),
      libraryDependencies ++= DependencyGroups.testRunners
    )

lazy val testRunners_spec2_2x: Project =
  newProject("testRunners_spec2_2x", file("scala/testRunners_spec2_2x"))
    .dependsOn(testRunners)
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
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
    scalacOptions := Seq(), // scala is disabled anyway, set empty options to move to a separate compiler profile (in IntelliJ model)
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
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      Compile/unmanagedJars += nailgunJar.value,
      packageFileMappings += nailgunJar.value -> "lib/jps/nailgun.jar",
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
      resolvers += "intellij-dependencies" at "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/",
      libraryDependencies += "org.jetbrains.intellij.deps.completion" % "completion-ranking-scala" % "0.3.2"
    )
    
lazy val packageSearchIntegration =
  newProject("packagesearch", file("scala/integration/packagesearch"))
    .dependsOn(scalaImpl)
    .settings(
      intellijPlugins += "com.jetbrains.packagesearch.intellij-plugin".toPlugin,
    )


// Utility projects

lazy val runtimeDependencies =
  (project in file("target/tools/runtime-dependencies"))
    .enablePlugins(LocalRepoPackager)
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
      localRepoDependencies := List(
        sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.binary_0_13),
        sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.binary_1_0),
        sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_0_13),
        sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_1_0),
        sbtDep("org.jetbrains.scala", "sbt-idea-compiler-indices", Versions.compilerIndicesVersion, Versions.Sbt.binary_0_13),
        sbtDep("org.jetbrains.scala", "sbt-idea-compiler-indices", Versions.compilerIndicesVersion, Versions.Sbt.binary_1_0)
      ),
      update := {
        localRepoUpdate.value
        update.value
      },
      packageFileMappings ++= {
        localRepoUpdate.value.map { case (src, trg) =>
          val targetPath = Paths.get("repo").resolve(trg)
          src.toFile -> targetPath.toString
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

(ThisBuild / communityFullClasspath) :=
  deduplicatedClasspath((scalaCommunity / Test / fullClasspath).value, (scalaCommunity / Compile / fullClasspath).value)

