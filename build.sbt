import Common.*
import CompilationCache.compilationCacheSettings
import Dependencies.provided
import DynamicDependenciesFetcher.*
import LocalRepoPackager.{localRepoDependencies, localRepoUpdate, relativeJarPath, sbtDep}
import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.PluginJars

import java.nio.file.Path

// Global build settings

(ThisBuild / intellijPluginName) := "Scala"

(ThisBuild / intellijBuild) := Versions.intellijVersion

(ThisBuild / intellijPlatform) := (Global / intellijPlatform).??(IntelliJPlatform.IdeaCommunity).value

(ThisBuild / autoRemoveOldCachedIntelliJSDK) := true
(ThisBuild / autoRemoveOldCachedDownloads) := true

ThisBuild / resolvers := {
//  not exactly sure why "releases" and "staging" would ever need to be enabled
//  Resolver.sonatypeOssRepos("releases") ++
//  Resolver.sonatypeOssRepos("staging") ++
//  enable if you need to resolve SNAPSHOT versions of open source libraries
//  Resolver.sonatypeOssRepos("snapshots") ++
//  enable if you need to resolve Scala 2.12, 2.13 RC versions
//  Seq(
//    "scala-integration" at "https://scala-ci.typesafe.com/artifactory/scala-integration/"
//  ) ++
  Seq(
    "JetBrains Maven Central" at "https://cache-redirector.jetbrains.com/maven-central"
  )
}

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

(Global / javacOptions) := globalJavacOptions

(Global / scalacOptions) := globalScalacOptions

Global / intellijAttachSources := true

// Muted lint warnings for keys used by the IDE, but not by sbt (coming from sbt-ide-settings)
Global / excludeLintKeys ++= Set(idePackagePrefix, ideSkipProject, ideExcludedDirectories, ideaConfigOptions, intellijPlugins)

val definedTestsScopeFilter: ScopeFilter =
  ScopeFilter(inDependencies(scalaCommunity, includeRoot = false), inConfigurations(Test))

// Main projects
lazy val scalaCommunity: sbt.Project =
  newProject("scalaCommunity", file("."))
    .dependsOn(
      bsp % "test->test;compile->compile",
      bspJUnit % "test->test;compile->compile",
      bspTerminal % "test->test;compile->compile",
      codeInsight % "test->test;compile->compile",
      conversion % "test->test;compile->compile",
      uast % "test->test;compile->compile",
      worksheet % "test->test;compile->compile",
      scalaImpl % "test->test;compile->compile",
      scalaMetaImpl % "test->test;compile->compile",
      structureView % "test->test;compile->compile",
      sbtImpl % "test->test;compile->compile",
      sbtProjectImportingTests % "test->test",
      compilerIntegration % "test->test;compile->compile",
      scalaCompilerPluginTests % "test->test;compile->compile",
      debugger % "test->test;compile->compile",
      testingSupport % "test->test;compile->compile",
      gradleIntegration % "test->test;compile->compile",
      intelliLangIntegration % "test->test;compile->compile",
      markdownIntegration % "test->test;compile->compile",
      mavenIntegration % "test->test;compile->compile",
      junitIntegration % "test->test;compile->compile",
      propertiesIntegration % "test->test;compile->compile",
      mlCompletionIntegration % "test->test;compile->compile",
      textAnalysis % "test->test;compile->compile",
      kotlinUtils % "test->test;compile->compile",
      structuralSearch % "test->test;compile->compile",
      scalaLanguageUtils % "test->test;compile->compile",
      scalaLanguageUtilsRt % "test->test;compile->compile",
      pluginXml,
      scalaCli % "test->test;compile->compile",
      javaDecompilerIntegration % "test->test", //add only test dependency to run tests from this module
      scalastyleIntegration
    )
    .settings(MainProjectSettings)
    .settings(
      packageAdditionalProjects := Seq(
        jps,
        compilerJps,
        repackagedZinc,
        worksheetReplInterfaceImpls,
        compileServer,
        scalaCompilerPlugin_2_12,
        scalaCompilerPlugin_2_13,
        scalaCompilerPlugin_3_3,
        nailgunRunners,
        copyrightIntegration,
        devKitIntegration,
        featuresTrainerIntegration,
        intellijBazelIntegration,
        javaDecompilerIntegration,
        runtimeDependencies
      ),
      // all sub-project tests need to be run within main project's classpath
      Test / definedTests := definedTests.all(definedTestsScopeFilter).value.flatten,
      cleanAll := Common.cleanAllTask(None).value
    )

lazy val pluginXml = newProject("pluginXml", file("pluginXml"))
  .settings(NoSourceDirectories)
  .settings(
    packageMethod := PackagingMethod.Standalone(),
    packageFileMappings += {
      val patchedVersionFile: File = Common.patchPluginXML(baseDirectory.value / "resources" / "META-INF" / "plugin.xml")
      streams.value.log.info(s"patched version in file: ${patchedVersionFile.getPath}")
      patchedVersionFile -> "lib/pluginXml.jar!/META-INF/plugin.xml"
    },
  )

lazy val scalaApi = newProject(
  "scala-api",
  file("scala/scala-api")
).settings(
  idePackagePrefix := Some("org.jetbrains.plugins.scala")
)

lazy val workspaceEntities = newProjectWithKotlin("workspace-entities", file("sbt/sbt-impl/workspace-entities"))
  .settings(
    Compile / unmanagedSourceDirectories ++= Seq(baseDirectory.value/"gen"),
    scalaVersion := Versions.scala3Version,
    Compile / scalacOptions := globalScala3ScalacOptions
  )

lazy val sbtKotlinUtils = newProjectWithKotlin("sbt-kotlin-utils", file("sbt/sbt-kotlin-utils"))
  .settings(
    crossPaths := false,
    autoScalaLibrary := false
  )

lazy val sbtApi =
  newProject("sbt-api", file("sbt/sbt-api"))
    .dependsOn(scalaApi, compilerShared, workspaceEntities)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      buildInfoPackage := "org.jetbrains.sbt.buildinfo",
      buildInfoKeys := Seq(
        "sbtStructureVersion" -> Versions.sbtStructureVersion,
        "sbtIdeaShellVersion" -> Versions.sbtIdeaShellVersion,
        "sbtIdeaCompilerIndicesVersion" -> Versions.compilerIndicesVersion,
        "sbtStructurePath_0_13" -> relativeJarPath(Dependencies.structureExtractor_0_13),
        "sbtStructurePath_1_0" -> relativeJarPath(Dependencies.structureExtractor_1_0),
        "sbtStructurePath_1_3" -> relativeJarPath(Dependencies.structureExtractor_1_3),
        "sbtStructurePath_2" -> relativeJarPath(Dependencies.structureExtractor_2),
      ),
      buildInfoOptions += BuildInfoOption.ConstantValue
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
  scalaImpl % "test->test;compile->compile",
).settings(
  intellijPlugins += "JUnit".toPlugin
)

lazy val worksheet =
  newProject("worksheet", file("scala/worksheet"))
    .dependsOn(
      bsp,
      compilerIntegration % "test->test;compile->compile",
      worksheetReplInterface,
      repl % "test->test;compile->compile", //do we indeed need this dependency on Scala REPL? can we get rid of it?
    ).settings(
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.worksheet")
    )

// A subproject which exists only to hold a precompiled jar in its `lib` directory.
lazy val worksheetReplInterface =
  Project("worksheet-repl-interface", file("scala/worksheet/repl-interface"))
    .settings(NoSourceDirectories)
    .settings(
      organization := JetBrains,
      crossPaths := false,
      autoScalaLibrary := false,
      ideSkipProject := true,
      intellijMainJars := Seq.empty,
      intellijTestJars := Seq.empty,
      intellijPlugins := Seq.empty,
      // Settings for packaging unmanaged jar dependencies in the plugin (jar files in the `lib` project directory).
      // Without this definition of `packageFileMappings`, the unmanaged jar dependencies are ignored.
      packageMethod := PackagingMethod.DepsOnly(),
      packageFileMappings := {
        val unmanagedJars = ((Compile / unmanagedBase).value ** "*.jar").get()
        unmanagedJars.map(f => (f, s"lib/${f.getName}"))
      }
    )

// A subproject which exists only to hold a precompiled jar in its `lib` directory.
lazy val worksheetReplInterfaceImpls =
  Project("worksheet-repl-interface-impls", file("scala/worksheet/repl-interface/impls"))
    .settings(NoSourceDirectories)
    .settings(
      organization := JetBrains,
      crossPaths := false,
      autoScalaLibrary := false,
      ideSkipProject := true,
      intellijMainJars := Seq.empty,
      intellijTestJars := Seq.empty,
      intellijPlugins := Seq.empty,
      // Settings for packaging unmanaged jar dependencies in the plugin (jar files in the `lib` project directory).
      // Without this definition of `packageFileMappings`, the unmanaged jar dependencies are ignored.
      packageMethod := PackagingMethod.DepsOnly(),
      packageFileMappings := {
        val unmanagedJars = ((Compile / unmanagedBase).value ** "*.jar").get()
        unmanagedJars.map(f => (f, s"worksheet-repl-interface/${f.getName}"))
      }
    )

lazy val structureView = newProject("structure-view", file("scala/structure-view"))
  .dependsOn(scalaImpl % "test->test;compile->compile")
  .settings(
    scalaVersion := Versions.scala3Version,
    Compile / scalacOptions := globalScala3ScalacOptions,
  )

lazy val repl = newProject("repl", file("scala/repl"))
  .dependsOn(
    scalaImpl % "test->test;compile->compile",
    structureView % "test->test;compile->compile",
  )
  .settings(
    scalaVersion := Versions.scala3Version,
    Compile / scalacOptions := globalScala3ScalacOptions,
    packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity)
  )

lazy val tastyReader = Project("tasty-reader", file("scala/tasty-reader"))
  .dependsOn(scalaLanguageUtils)
  .dependsOn(scalaLanguageUtilsRt)
  .settings(projectDirectoriesSettings)
  .settings(
    name := "tasty-reader",
    organization := JetBrains,
    idePackagePrefix := Some("org.jetbrains.plugins.scala.tasty.reader"),
    intellijMainJars := Seq.empty,
    intellijTestJars := Seq.empty,
    scalaVersion := Versions.scala3Version,
    libraryDependencies += Dependencies.tastyCore,
    (Compile / scalacOptions) := Seq("-deprecation"),
    (Test / unmanagedResourceDirectories) += baseDirectory.value / "testdata",
    libraryDependencies ++= Seq(
      Dependencies.junit % Test,
      Dependencies.junitInterface % Test,
    )
  )
  .settings(compilationCacheSettings)

lazy val packageSearchClient: sbt.Project =
  newProjectWithKotlin("package-search-client", file("scala/package-search-client"))
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      resolvers += DependencyResolvers.PackageSearch,
      libraryDependencies ++= Dependencies.packageSearchDependencies,
    )

lazy val scalaImpl: sbt.Project =
  newProject("scala-impl", file("scala/scala-impl"))
    .dependsOn(
      compilerShared,
      scalaApi,
      scalaLanguageUtils,
      sbtApi,
      decompiler % "test->test;compile->compile",
      tastyReader % "test->test;compile->compile",
      scalatestFinders,
      runners,
      testRunners,
      packageSearchClient % "test->test;compile->compile"
    )
    .settings(
      ideExcludedDirectories := Seq(
        baseDirectory.value / "target",
        baseDirectory.value / "testdata" / "projectsForHighlightingTests" / ".ivy_cache",
        baseDirectory.value / "testdata" / "projectsForHighlightingTests" / ".coursier_cache",
        //NOTE: when updating, please also update `org.jetbrains.scalateamcity.common.Caching.highlightingPatterns`
        baseDirectory.value / "testdata" / "projectsForHighlightingTests" / "downloaded",
      ),
      //scalacOptions in Global += "-Xmacro-settings:analyze-caches",
      libraryDependencies ++= DependencyGroups.scalaCommunity,

      libraryDependencies ++= Seq(
        //for ExternalSystemTestCase and ExternalSystemImportingTestCase
        Dependencies.intellijExternalSystemTestFramework % Test,
        Dependencies.slf4jApi % Test,
        //for PlatformTestUtil.newPerformanceTest
        Dependencies.intellijIdeMetricsBenchmark % Test,
        Dependencies.intellijIdeMetricsCollector % Test,
        Dependencies.intellijIdeUtilCommon % Test,
      ),
      // for dependency version completion/inspections
      libraryDependencies += Dependencies.coursierApi,
      resolvers += Versions.intellijRepository_ForManagedIntellijDependencies,
      intellijPlugins += "JUnit".toPlugin,
      intellijPluginJars := intellijPluginJars.value.map { case PluginJars(descriptor, root, cp) =>
        PluginJars(descriptor, root, cp.filterNot(_.getName.contains("junit-jupiter-api")))
      },
      packageLibraryMappings := Seq(
        // "com.thesamet.scalapb" %% "scalapb-runtime" % ".*" -> None,
        // "com.thesamet.scalapb" %% "lenses" % ".*"          -> None,
        Dependencies.scalaXml               -> Some("lib/scala-xml.jar"),
        Dependencies.scalaReflect           -> Some("lib/scala-reflect.jar"),
        Dependencies.scalaParserCombinators -> Some("lib/scala-parser-combinators.jar"),
        Dependencies.scalaLibrary           -> None,
        Dependencies.scala3Library          -> None
      )
    )

/**
 * Encapsulates scala.meta, so that we don't have to depend on in directly in scala-impl
 */
lazy val scalaMetaImpl: sbt.Project =
  newProject("scala-meta-impl", file("scala/scala-meta-impl"))
    .dependsOn(scalaImpl % "test->test;compile->compile")
    .settings(
      scalaVersion := Versions.scalaVersion,
      libraryDependencies ++= Seq(
        Dependencies.scalaMetaCore,
        Dependencies.scalapbRuntime
      ),
    )

/**
 * Utilities for Kotlin-Scala interop written in Kotlin
 */
lazy val kotlinUtils: sbt.Project =
  newProjectWithKotlin("kotlin-utils", file("scala/kotlin-utils"))
    .dependsOn(scalaLanguageUtils)
    .settings(
      idePackagePrefix := Some("org.jetbrains.plugins.scala.kotlin.util"),
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity),
    )

/**
 * Utilities which only depend on the Scala standard library and do not depend on other libraries or IntelliJ SDK
 */
lazy val scalaLanguageUtils: sbt.Project =
  newPlainScalaProject("scala-utils-language", file("scala/scala-utils-language"))
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity)
    )

/**
 * Same as [[scalaLanguageUtils]], but utilities from this module can be used form both IntelliJ IDEA process and JPS process.
 * Keep this module as small as possible with no other dependencies
 */
lazy val scalaLanguageUtilsRt: sbt.Project =
  newPlainScalaProject("scala-utils-language-rt", file("scala/scala-utils-language-rt"))
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := outOfIDEAProcessScala3ScalacOptions,
      Compile / javacOptions := outOfIDEAProcessJavacOptions,
      packageMethod := PackagingMethod.Standalone("lib/utils_rt.jar", static = true),
    )

lazy val sbtImpl =
  newProject("sbt-impl", file("sbt/sbt-impl"))
    .dependsOn(
      sbtApi,
      sbtKotlinUtils,
      scalaImpl % "test->test;compile->compile",
    )
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      intellijPlugins += "org.jetbrains.idea.maven".toPlugin,
      libraryDependencies += Dependencies.sbtStructureCore.exclude("org.scala-lang.modules", "scala-xml_3")
    )

lazy val sbtProjectImportingTests =
  newProject("sbt-project-importing-tests", file("sbt/sbt-project-importing-tests"))
    .dependsOn(
      sbtImpl % "compile->compile;test->test",
      // this dependency is added primarily use CompileServerLauncher from sbt importing test (to shut it down)
      compilerIntegration
    )

lazy val compilerIntegration =
  newProject("compiler-integration", file("scala/compiler-integration"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      codeInsight % "test->test;compile->compile",
      sbtImpl % "test->test;compile->compile",
      scalaMetaImpl % "test->test;compile->compile",
      jps,
      bsp
    )
    .settings(
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.compiler-integration")
    )

lazy val debugger =
  newProject("debugger", file("scala/debugger"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      compilerIntegration % "test->test;compile->compile"
    ).settings(
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.debugger")
    )


lazy val compileServer =
  newPlainScalaProject("compile-server", file("scala/compile-server"))
    .dependsOn(compilerShared, repackagedZinc, worksheetReplInterface)
    .withJpsClasspath
    .settings(
      Compile / javacOptions := outOfIDEAProcessJavacOptions,
      Compile / scalacOptions := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/jps/compile-server.jar", static = true),
      libraryDependencies += Dependencies.nailgun,
      packageLibraryMappings += Dependencies.nailgun -> Some("lib/jps/nailgun.jar")
    )

// Compiler plugins

def compilerPluginProject(
  projectName: String,
  projectPath: File,
  scalaCompilerVersion: String,
  extraScalacOptions: Seq[String],
  packagingOutputPath: String
): sbt.Project =
  Project(projectName, projectPath)
    .disablePlugins(SbtIdeaPluginExtension)
    .settings(
      name := projectName,
      organization := JetBrains,
      intellijPlugins := Seq.empty,
      scalaVersion := scalaCompilerVersion,
      libraryDependencies += {
        val artifact = scalaCompilerVersion match {
          case scala3 if scala3.startsWith("3.") => "org.scala-lang" %% "scala3-compiler" % scala3
          case scala2 => "org.scala-lang" % "scala-compiler" % scala2
        }
        artifact % Provided
      },
      libraryDependencies ++= Seq(
        Dependencies.jetbrainsAnnotations % Provided,
        Dependencies.junit % Test,
        Dependencies.junitInterface % Test,
        Dependencies.opentest4j % Test
      ),
      Compile / scalacOptions := Seq("--release", "8") ++ extraScalacOptions,
      packageMethod := PackagingMethod.Standalone(packagingOutputPath),
      Test / fork := true
    )
    .settings(projectDirectoriesSettings)
    .settings(compilationCacheSettings)

lazy val scalaCompilerPlugin_2_12: sbt.Project =
  compilerPluginProject(
    projectName = "compiler-plugin-2_12",
    projectPath = file("scala/compiler-plugin/scala-2.12"),
    scalaCompilerVersion = "2.12.20",
    extraScalacOptions = Seq("-deprecation"),
    packagingOutputPath = "lib/jps/compiler-plugin-2.12.jar"
  )

lazy val scalaCompilerPlugin_2_13: sbt.Project =
  compilerPluginProject(
    projectName = "compiler-plugin-2_13",
    projectPath = file("scala/compiler-plugin/scala-2.13"),
    scalaCompilerVersion = "2.13.15",
    extraScalacOptions = Seq("-deprecation"),
    packagingOutputPath = "lib/jps/compiler-plugin-2.13.jar"
  )

lazy val scalaCompilerPlugin_3_3: sbt.Project =
  compilerPluginProject(
    projectName = "compiler-plugin-3_3",
    projectPath = file("scala/compiler-plugin/scala-3.3"),
    scalaCompilerVersion = "3.3.6",
    extraScalacOptions = Seq.empty,
    packagingOutputPath = "lib/jps/compiler-plugin-3.3.jar"
  )

lazy val scalaCompilerPluginTests: sbt.Project =
  newProject("compiler-plugin-tests", file("scala/compiler-plugin/tests"))
    .dependsOn(scalaImpl)

lazy val compilerJps =
  newPlainScalaProject("compiler-jps", file("scala/compiler-jps"))
    .dependsOn(jps, compileServer)
    .withJpsClasspath
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/jps/compiler-jps.jar", static = true),
      libraryDependencies ++= Seq(Dependencies.scalaParallelCollections),
      packageLibraryMappings ++= Seq(
        Dependencies.scalaParallelCollections -> Some("lib/jps/scala-parallel-collections.jar")
      ),
    )

lazy val repackagedZinc =
  newProject("repackagedZinc", file("target/tools/zinc"))
    .settings(NoSourceDirectories)
    .settings(
      packageOutputDir := baseDirectory.value / "plugin",
      packageAssembleLibraries := true,
      shadePatterns += ShadePattern("com.google.protobuf.**", "zinc.protobuf.@1"),
      packageMethod := PackagingMethod.DepsOnly("lib/jps/incremental-compiler.jar"),
      libraryDependencies ++= Seq(Dependencies.zinc, Dependencies.compilerInterface, Dependencies.sbtInterface),
      // We package and ship these jars separately. They are also transitive dependencies of `zinc`.
      // These mappings ensure that the transitive dependencies are not packaged into the assembled
      // `incremental-compiler.jar`, which leads to a bloated classpath with repeated classes.
      packageLibraryMappings ++= Seq(
        Dependencies.compilerInterface -> None,
        Dependencies.sbtInterface -> None
      )
    )

lazy val compilerShared =
  newPlainScalaProject("compiler-shared", file("scala/compiler-shared"))
    .dependsOn(scalaLanguageUtilsRt)
    .withJpsSharedClasspath
    .settings(
      scalaVersion := Versions.scala3Version,
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScala3ScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/compiler-shared.jar", static = true)
    )

lazy val jps =
  newPlainScalaProject("jps", file("scala/jps"))
    .withJpsClasspath
    .settings(
      Compile / javacOptions := outOfIDEAProcessJavacOptions,
      Compile / scalacOptions := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/scala-jps.jar", static = true),
      libraryDependencies += Dependencies.compilerIndicesProtocol,
      packageLibraryMappings += Dependencies.compilerIndicesProtocol -> Some(s"lib/scala-compiler-indices-protocol_2.13-${Versions.compilerIndicesVersion}.jar")
    )

lazy val runners: Project =
  newProject("runners", file("scala/runners"))
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone(static = true),
      packageAdditionalProjects ++= Seq(testRunners, testRunners_spec2_2x)
    )

lazy val structuralSearch =
  newProject("structural-search", file("scala/structural-search"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      codeInsight % "test->test;compile->compile"
    )
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      intellijPlugins += "JUnit".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.structural-search")
    )

lazy val testingSupport =
  newProject("testing-support", file("scala/test-integration/testing-support"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      sbtImpl % "test->test;compile->compile",
      bsp,
      structureView % "test->test;compile->compile",
      compilerIntegration % "test->test;compile->compile"
    )
    .settings(
      intellijPlugins += "JUnit".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.testing-support")
    )

lazy val testRunners: Project =
  newProject("test-runners", file("scala/test-integration/test-runners"))
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.MergeIntoOther(runners),
      libraryDependencies ++= DependencyGroups.testRunners
    )

lazy val testRunners_spec2_2x: Project =
  newProject("test-runners-spec2_2x", file("scala/test-integration/test-runners-spec2_2x"))
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
    name := "scalatest-finders",
    organization := JetBrains,
    scalaVersion := Versions.scalaVersion,
    // NOTE: we might continue NOT using Scala in scalatestFinders just in case
    // in some future we will decide again to extract the library, so as it can be used even without scala jar
    crossPaths := false, // disable using the Scala version in output paths and artifacts
    autoScalaLibrary := false, // removes Scala dependency,
    scalacOptions := Seq(), // scala is disabled anyway, set empty options to move to a separate compiler profile (in IntelliJ model)
    javacOptions := globalJavacOptions, // finders are run in IDEA process, so using JDK 17
    packageMethod := PackagingMethod.Standalone("lib/scalatest-finders-patched.jar"),
    intellijMainJars := Nil,
    intellijTestJars := Nil,
  )
  .settings(compilationCacheSettings)

lazy val scalatestFindersTestSettings = Seq(
  scalacOptions := Seq("-deprecation")
)
val scalatestLatest_2   = "2.2.6"
val scalatestLatest_3_0 = "3.0.9"
val scalatestLatest_3_2 = "3.2.16"

lazy val scalatestFindersTests_2 = Project("scalatest-finders-tests-2", scalatestFindersRootDir / "tests-2")
  .dependsOn(scalatestFinders)
  .settings(
    name := "scalatest-finders-tests-2",
    organization := JetBrains,
    scalatestFindersTestSettings,
    scalaVersion := "2.11.12",
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_2 % Test),
    intellijMainJars := Nil,
    intellijTestJars := Nil
  )
  .settings(compilationCacheSettings)

lazy val scalatestFindersTests_3_0 = Project("scalatest-finders-tests-3_0", scalatestFindersRootDir / "tests-3_0")
  .dependsOn(scalatestFinders)
  .settings(
    name := "scalatest-finders-tests-3_0",
    organization := JetBrains,
    scalatestFindersTestSettings,
    scalaVersion := "2.12.20",
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_3_0 % Test),
    intellijMainJars := Nil,
    intellijTestJars := Nil,
  )
  .settings(compilationCacheSettings)

lazy val scalatestFindersTests_3_2 = Project("scalatest-finders-tests-3_2", scalatestFindersRootDir / "tests-3_2")
  .dependsOn(scalatestFinders)
  .settings(
    name := "scalatest-finders-tests-3_2",
    organization := JetBrains,
    scalatestFindersTestSettings,
    scalaVersion := Versions.scalaVersion,
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_3_2 % Test),
    intellijMainJars := Nil,
    intellijTestJars := Nil,
  )
  .settings(compilationCacheSettings)

lazy val nailgunRunners =
  newProject("nailgun", file("scala/nailgun"))
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      libraryDependencies += Dependencies.nailgun,
      packageLibraryMappings += Dependencies.nailgun -> Some("lib/jps/nailgun.jar"),
      packageMethod := PackagingMethod.Standalone("lib/scala-nailgun-runner.jar", static = true)
    )

lazy val decompiler =
  newProject("decompiler", file("scala/decompiler"))
    .dependsOn(scalaLanguageUtils)
    .settings(
      libraryDependencies ++= DependencyGroups.decompiler,
      packageMethod := PackagingMethod.Standalone("lib/scalap.jar")
    )

lazy val bspJUnit =
  newProject("bsp-junit", file("bsp-builtin/bsp-junit"))
    .dependsOn(
      bsp % "test->test;compile->compile",
    )
    .settings(
      intellijPlugins += "JUnit".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.bsp-junit")
    )

lazy val bspTerminal =
  newProject("bsp-terminal", file("bsp-builtin/bsp-terminal"))
    .dependsOn(
      bsp % "test->test;compile->compile",
    )
    .settings(
      intellijPlugins += "org.jetbrains.plugins.terminal".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.bsp-terminal")
    )

lazy val bsp =
  newProject("bsp", file("bsp-builtin/bsp"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      sbtImpl % "test->test;compile->compile"
    )
    .settings(
      libraryDependencies ++= DependencyGroups.bsp,
      buildInfoPackage := "org.jetbrains.bsp.buildinfo",
      buildInfoKeys := Seq("bloopVersion" -> Versions.bloopVersion),
      buildInfoOptions += BuildInfoOption.ConstantValue,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.bsp"),
    )

lazy val scalaCli =
  newProject("scala-cli", file("scala-cli"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      bsp % "test->test;compile->compile",
    ).settings(
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.scala-cli")
    )

// Integration with other IDEA plugins
//TODO: rename the module module and maybe base packages (check external usages)
// it actually doesn't have anything related to actual devkit integration, it doesn't depend on anything from it
// it's similar to DevKit plugin in it's purpose, but it's different.
// It just contains some internal actions required for Scala Plugin development (or other Scala-based plugins using sbt-idea-plugin)
lazy val devKitIntegration =
  newProject("devKit", file("scala/integration/devKit"))
    .dependsOn(scalaImpl, sbtImpl)
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.devkit")
    )

lazy val copyrightIntegration =
  newProject("copyright", file("scala/integration/copyright"))
    .dependsOn(scalaImpl)
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      intellijPlugins += "com.intellij.copyright".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.copyright")
    )

lazy val gradleIntegration =
  newProject("gradle", file("scala/integration/gradle"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      sbtImpl % "test->test;compile->compile",
      compilerIntegration % "test->test;compile->compile"
    )
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      intellijPlugins ++= Seq(
        "com.intellij.gradle",     // required by Android
        "org.intellij.groovy",     // required by Gradle
        "com.intellij.properties"  // required by Gradle
      ).map(_.toPlugin),
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.gradle")
    )

lazy val intellijBazelIntegration =
  newProject("intellij-bazel", file("scala/integration/intellij-bazel"))
    .dependsOn(scalaImpl, sbtImpl)
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      intellijPlugins += "org.jetbrains.bazel::super-early-bird".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.intellij-bazel")
    )

lazy val intelliLangIntegration = newProject(
  "intelliLang",
  file("scala/integration/intellilang")
).dependsOn(
  scalaImpl % "test->test;compile->compile"
).settings(
//  addCompilerPlugin(Dependencies.macroParadise),
  intellijPlugins ++= Seq(
    "com.intellij.modules.json"
  ).map(_.toPlugin),
  packageMethod := PackagingMethod.PluginModule("scalaCommunity.intelliLang"),
)

lazy val markdownIntegration =
  newProject("markdown", file("scala/integration/markdown"))
    .dependsOn(scalaApi)
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      idePackagePrefix := Some("org.jetbrains.plugins.scala.markdown"),
      intellijPlugins += "org.intellij.plugins.markdown".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.markdown"),
    )

lazy val mavenIntegration =
  newProject("maven", file("scala/integration/maven"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      testingSupport,
      sbtImpl % "test->test",
      compilerIntegration % "test->test;compile->compile"
    )
    .settings(
      intellijPlugins += "org.jetbrains.idea.maven".toPlugin,
      intellijPlugins += "org.jetbrains.idea.reposearch".toPlugin, // required for Maven (IJPL-35276)
      libraryDependencies ++= Seq(
        Dependencies.intellijMavenTestFramework % Test,
        Dependencies.intellijEelJavaTestFramework % Test
      ),
      resolvers += Versions.intellijRepository_ForManagedIntellijDependencies,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.maven")
    )

lazy val junitIntegration =
  newProject("junit", file("scala/integration/junit"))
    .dependsOn(sbtImpl % "compile->compile")
    .settings(
      intellijPlugins += "JUnit".toPlugin
    )

lazy val propertiesIntegration =
  newProject("properties", file("scala/integration/properties"))
    .dependsOn(scalaImpl % "test->test;compile->compile", sbtImpl)
    .settings(
      intellijPlugins ++= Seq(
        "com.intellij.properties".toPlugin,
        "com.intellij.java-i18n".toPlugin,
      )
    )

lazy val javaDecompilerIntegration =
  newProject("java-decompiler", file("scala/integration/java-decompiler"))
    .dependsOn(scalaImpl % "compile->compile;test->test")
    .settings(
      intellijPlugins += "org.jetbrains.java.decompiler".toPlugin,
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity)
    )

lazy val mlCompletionIntegration =
  newProject("ml-completion", file("scala/integration/ml-completion"))
    .dependsOn(scalaImpl, sbtImpl)
    .settings(
      intellijPlugins += "com.intellij.completion.ml.ranking".toPlugin,
      resolvers += DependencyResolvers.IntelliJDependencies,
      libraryDependencies += "org.jetbrains.intellij.deps.completion" % "completion-ranking-scala" % "0.4.1"
    )

lazy val scalastyleIntegration = newProject("scalastyle", file("scala/integration/scalastyle"))
  .dependsOn(scalaImpl)
  .settings(
    libraryDependencies += Dependencies.scalastyle,
    packageLibraryMappings += (Dependencies.scalaCollectionCompat -> Some("lib/scala-collection-compat.jar"))
  )

//Integration with:
// - Built-in spellchecker (see com.intellij.spellchecker package)
// - Grazie plugin (more advanced spell + grammar checker)
lazy val textAnalysis =
  newProject("textAnalysis", file("scala/integration/textAnalysis"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      intelliLangIntegration //uses logic related to parsing interpolated strings
    )
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      intellijPlugins += "tanvd.grazi".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.textAnalysis")
    )

lazy val featuresTrainerIntegration =
  newProject("features-trainer", file("scala/integration/features-trainer"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
    )
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      intellijPlugins += "training".toPlugin,
      packageMethod := PackagingMethod.PluginModule("scalaCommunity.featuresTrainer")
    )

// SCL-20376 - The package search plugin will be replaced by a new one, requiring a rewrite of the integration code.
//lazy val packageSearchIntegration =
//  newProject("packagesearch", file("scala/integration/packagesearch"))
//    .dependsOn(scalaImpl, sbtImpl)
//    .settings(
//      // The packageSearch plugin is no longer distributed with IDEA. It will soon be available on the plugin
//      // marketplace once more and this workaround will be unnecessary.
//      // TODO: use `intellijVersion_ForManagedIntellijDependencies` as version once the plugin is published properly
//      libraryDependencies += "com.jetbrains.intellij.packageSearch" % "package-search" % "232.6095-EAP-CANDIDATE-SNAPSHOT" % Provided notTransitive(),
//      resolvers += MavenRepository("intellij-repository-snapshots", "https://www.jetbrains.com/intellij-repository/snapshots"),
//      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity)
//    )

// Utility projects

lazy val runtimeDependencies = project.in(file("target/tools/runtime-dependencies"))
  .enablePlugins(DynamicDependenciesFetcher, LocalRepoPackager)
  .settings(NoSourceDirectories)
  .settings(
    name := "runtimeDependencies",
    organization := JetBrains,
    crossPaths := false,
    autoScalaLibrary := false,
    resolvers += Classpaths.sbtPluginReleases,
    ideSkipProject := true,
    packageMethod := PackagingMethod.DepsOnly(),
    dynamicDependencies := Seq(
      binaryDep("org.scala-sbt", "sbt-launch", Versions.sbtVersion) -> "launcher/sbt-launch.jar",
      binaryDep("org.scala-sbt", "util-interface", Versions.sbtVersion) -> "lib/jps/sbt-interface.jar",
      binaryDep("org.scala-sbt", "compiler-interface", Versions.zincVersion) -> "lib/jps/compiler-interface.jar",

      sourceDep("org.scala-sbt", "compiler-bridge", "2.10", Versions.zincVersion) -> "lib/jps/compiler-bridge-sources_2.10.jar",
      sourceDep("org.scala-sbt", "compiler-bridge", "2.11", Versions.zincVersion) -> "lib/jps/compiler-bridge-sources_2.11.jar",
      sourceDep("org.scala-sbt", "compiler-bridge", "2.13", Versions.zincVersion) -> "lib/jps/compiler-bridge-sources_2.13.jar",
      binaryDep("org.scala-lang", "scala3-sbt-bridge", "3.0.2") -> "lib/jps/scala3-sbt-bridge_3.0.jar",
      binaryDep("org.scala-lang", "scala3-sbt-bridge", "3.1.3") -> "lib/jps/scala3-sbt-bridge_3.1.jar",
      binaryDep("org.scala-lang", "scala3-sbt-bridge", "3.2.2") -> "lib/jps/scala3-sbt-bridge_3.2.jar",
      binaryDep("org.scala-lang", "scala3-sbt-bridge", "3.3.1") -> "lib/jps/scala3-sbt-bridge_3.3.1.jar",
      binaryDep("org.scala-lang", "scala3-sbt-bridge", "3.3.3") -> "lib/jps/scala3-sbt-bridge_3.3.jar",
      binaryDep("org.scala-lang", "scala3-sbt-bridge", "3.4.0") -> "lib/jps/scala3-sbt-bridge_3.4.jar",

      binaryDep("org.scala-sbt.rt", "java9-rt-export", Versions.java9rtExportVersion) -> "java9-rt-export/java9-rt-export.jar",
    ),
    localRepoDependencies := List(
      Dependencies.structureExtractor_0_13,
      Dependencies.structureExtractor_1_0,
      Dependencies.structureExtractor_1_3,
      Dependencies.structureExtractor_2,

      sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_0_13),
      sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_1_0),
      sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_2),

      // SCL-22858 compiler bytecode indices are disabled in sbt shell
      // sbtDep("org.jetbrains.scala", "sbt-idea-compiler-indices", Versions.compilerIndicesVersion, Versions.Sbt.binary_0_13),
      // sbtDep("org.jetbrains.scala", "sbt-idea-compiler-indices", Versions.compilerIndicesVersion, Versions.Sbt.binary_1_0)
    ),
    update := {
      dynamicDependenciesUpdate.value
      localRepoUpdate.value
      update.value
    },
    packageFileMappings ++= {
      localRepoUpdate.value.map { case (src, trg) =>
        val targetPath = Path.of("repo").resolve(trg)
        src.toFile -> targetPath.toString
      } ++
      dynamicDependenciesUpdate.value.map { case (src, trg) =>
        src.toFile -> trg.toString
      }
    }
  )
  .settings(compilationCacheSettings)

//lazy val jmhBenchmarks =
//  newProject("benchmarks", file("scala/benchmarks"))
//    .dependsOn(scalaImpl % "test->test")
//    .enablePlugins(JmhPlugin)

////////////////////////////////////////////
//
// Testing keys and settings
//
////////////////////////////////////////////
import Common.TestCategory.*

def runInputTask(key: InputKey[?], input: String, inState: State, resultState: State): State = {
  val extracted = Project.extract(inState)
  try {
    extracted.runInputTask(key, input, inState)
    resultState
  } catch {
    case _: Exception => resultState.fail
  }
}

def runTestsInTC(category: String): String =
  s"testOnly -- -v -s -a +c +q --include-categories=$category --exclude-categories=$flakyTests"

addCommandAlias("runFileSetTests", runTestsInTC(fileSetTests))
addCommandAlias("runCompilationTestsZinc", runTestsInTC(compilationTestsZinc))
addCommandAlias("runCompilationTestsIDEA", runTestsInTC(compilationTestsIDEA))
addCommandAlias("runCompilerHighlightingTests", runTestsInTC(compilerHighlightingTests))
addCommandAlias("runCompletionTests", runTestsInTC(completionTests))
addCommandAlias("runEditorTests", runTestsInTC(editorTests))
addCommandAlias("runSlowTests", runTestsInTC(slowTests))
addCommandAlias("runSlowTests2", runTestsInTC(slowTests2))
addCommandAlias("runDebuggerTests", runTestsInTC(debuggerTests))
addCommandAlias("runDebuggerEvaluationTests", runTestsInTC(debuggerEvaluationTests))
addCommandAlias("runScalacTests", runTestsInTC(scalacTests))
addCommandAlias("runTypeInferenceTests", runTestsInTC(typecheckerTests))
addCommandAlias("runTestingSupportTests", runTestsInTC(testingSupportTests))
addCommandAlias("runTextToTextTests", runTestsInTC(textToTextTests))
addCommandAlias("runWorksheetEvaluationTests", runTestsInTC(worksheetEvaluationTests))
addCommandAlias("runHighlightingTests", runTestsInTC(highlightingTests))
addCommandAlias("runNightlyTests", runTestsInTC(randomTypingTests))

addCommandAlias("runFlakyTests", s"testOnly -- -v -s -a +c +q --include-categories=$flakyTests")

//it's run during "Package" step on TC
addCommandAlias("runBundleSortingTests", runTestsInTC(bundleSortingTests))

lazy val categoriesToExclude = List(
  fileSetTests,
  compilationTestsZinc,
  compilationTestsIDEA,
  compilerHighlightingTests,
  completionTests,
  editorTests,
  slowTests,
  slowTests2,
  debuggerTests,
  debuggerEvaluationTests,
  scalacTests,
  typecheckerTests,
  testingSupportTests,
  textToTextTests,
  highlightingTests,
  worksheetEvaluationTests,
  randomTypingTests,
  flakyTests
)

def runFastTestsInTC(glob: String): String =
  s"testOnly $glob -- -v -s -a +c +q --exclude-categories=${categoriesToExclude.mkString(",")}"

addCommandAlias("runFastTests", runFastTestsInTC("*"))
// subsets of tests to split the complete test run into smaller chunks
addCommandAlias("runFastTestsComIntelliJ", runFastTestsInTC("com.intellij.*"))
addCommandAlias("runFastTestsOrgJetbrains", runFastTestsInTC("org.jetbrains.*"))
addCommandAlias("runFastTestsScala", runFastTestsInTC("scala.*"))

// Compiler plugin tests command definitions.
addCommandAlias("runCompilerPluginTests-2_12", "compiler-plugin-2_12/testOnly -- -v -s -a +c +q")
addCommandAlias("runCompilerPluginTests-2_13", "compiler-plugin-2_13/testOnly -- -v -s -a +c +q")
addCommandAlias("runCompilerPluginTests-3_3", "compiler-plugin-3_3/testOnly -- -v -s -a +c +q")
addCommandAlias("runCompilerPluginTests", "runCompilerPluginTests-2_12; runCompilerPluginTests-2_13; runCompilerPluginTests-3_3")
