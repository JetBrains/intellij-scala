import Common.*
import Dependencies.provided
import DynamicDependenciesFetcher.*
import LocalRepoPackager.{localRepoDependencies, localRepoUpdate, relativeJarPath, sbtDep}
import org.jetbrains.sbtidea.Keys.*

import java.nio.file.Path
import org.jetbrains.sbtidea.PluginJars

// Global build settings

(ThisBuild / intellijPluginName) := "Scala"

(ThisBuild / intellijBuild) := Versions.intellijVersion

(ThisBuild / intellijPlatform) := (Global / intellijPlatform).??(IntelliJPlatform.IdeaCommunity).value

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

val definedTestsScopeFilter: ScopeFilter =
  ScopeFilter(inDependencies(scalaCommunity, includeRoot = false), inConfigurations(Test))

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
      structureView % "test->test;compile->compile",
      sbtImpl % "test->test;compile->compile",
      compilerIntegration % "test->test;compile->compile",
      debugger % "test->test;compile->compile",
      testingSupport % "test->test;compile->compile",
      devKitIntegration % "test->test;compile->compile",
      gradleIntegration % "test->test;compile->compile",
      intelliLangIntegration % "test->test;compile->compile",
      mavenIntegration % "test->test;compile->compile",
      junitIntegration % "test->test;compile->compile",
      propertiesIntegration % "test->test;compile->compile",
      mlCompletionIntegration % "test->test;compile->compile",
      featuresTrainerIntegration % "test->test;compile->compile",
      textAnalysis % "test->test;compile->compile",
      scalaLanguageUtils % "test->test;compile->compile",
      scalaLanguageUtilsRt % "test->test;compile->compile",
      pluginXml,
      //We need this explicit dependency in the root project to ensure the module is compiled before any other module
      //It matters when the project is built as a "Before Run" step when executing run configuration.
      //In this case, it actually builds the module with all its dependencies, not the whole project.
      scalacPatches % Provided
    )
    .settings(MainProjectSettings *)
    .settings(
      packageAdditionalProjects := Seq(
        jps,
        compilerJps,
        repackagedZinc,
        worksheetReplInterfaceImpls,
        compileServer,
        nailgunRunners,
        copyrightIntegration,
        javaDecompilerIntegration,
        runtimeDependencies
      ),
      // all sub-project tests need to be run within main project's classpath
      Test / definedTests := definedTests.all(definedTestsScopeFilter).value.flatten
    )

lazy val pluginXml = newProject("pluginXml", file("pluginXml"))
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
  idePackagePrefix := Some("org.jetbrains.plugins.scala"),
)

lazy val workspaceEntities = newProjectWithKotlin("workspace-entities", file("sbt/sbt-impl/workspace-entities"))
  .settings(
    Compile / unmanagedSourceDirectories ++= Seq(baseDirectory.value/"gen"),
    scalaVersion := Versions.scala3Version,
    Compile / scalacOptions := globalScala3ScalacOptions
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
        "sbtLatest_0_13" -> Versions.Sbt.latest_0_13,
        "sbtLatest_1_0" -> Versions.Sbt.latest_1_0,
        "sbtLatestVersion" -> Versions.sbtVersion,
        "sbtStructurePath_0_13" -> relativeJarPath(sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, "0.13")),
        "sbtStructurePath_1_2" -> relativeJarPath(sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, "1.2")),
        "sbtStructurePath_1_3" -> relativeJarPath(sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, "1.3"))
      ),
      buildInfoOptions += BuildInfoOption.ConstantValue
    )
    .withCompilerPluginIn(scalacPatches)

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
  .withCompilerPluginIn(scalacPatches)

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
      worksheetReplInterface % "test->test;compile->compile",
      repl % "test->test;compile->compile", //do we indeed need this dependency on Scala REPL? can we get rid of it?
    )

lazy val worksheetReplInterface =
  Project("worksheet-repl-interface", file("scala/worksheet-repl-interface"))
    .settings(projectDirectoriesSettings)
    .settings(
      name := "worksheet-repl-interface",
      organization := "JetBrains",
      // NOTE: we might continue NOT using Scala in scalatestFinders just in case
      // in some future we will decide again to extract the library, so as it can be used even without scala jar
      scalaVersion := Versions.scalaVersion,
      crossPaths := false, // disable using the Scala version in output paths and artifacts
      autoScalaLibrary := false, // removes Scala dependency
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions, // can run in the compile server
      (Compile / scalacOptions) := Seq.empty, // scala is disabled anyway, set empty options to move to a separate compiler profile (in IntelliJ model)
      packageMethod :=  PackagingMethod.Standalone("lib/repl-interface.jar", static = true),
      intellijMainJars := Seq.empty,
      intellijTestJars := Seq.empty,
      intellijPlugins := Seq.empty
    )

lazy val worksheetReplInterfaceImpls: Project =
  newProject("worksheet-repl-interface-impls", file("scala/worksheet-repl-interface-impls"))
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("worksheet-repl-interface/impls.jar", static = true),
      packageAdditionalProjects := Seq(
        worksheetReplInterfaceImpl_2_12,
        worksheetReplInterfaceImpl_2_12_13,
        worksheetReplInterfaceImpl_2_13_0,
        worksheetReplInterfaceImpl_2_13,
        worksheetReplInterfaceImpl_2_13_12,
        worksheetReplInterfaceImpl_3_0_0,
        worksheetReplInterfaceImpl_3_1_2,
        worksheetReplInterfaceImpl_3_3_0
      )
    )

def worksheetReplInterfaceImplCommonSettings(scalaVer: String): Seq[Setting[?]] = Seq(
  scalaVersion := scalaVer,
  // protobuf-java is excluded to avoid showing outdated vulnerable dependencies, and it is also not necessary for
  // compiling the worksheet repl interfaces
  libraryDependencies += {
    if (scalaVer.startsWith("3."))
      "org.scala-lang" %% "scala3-compiler" % scalaVer % Provided exclude("com.google.protobuf", "protobuf-java")
    else
      "org.scala-lang" % "scala-compiler" % scalaVer % Provided
  },
  // override the Scala 2.13 library dependency in the Scala 3 worksheet repl interfaces
  // this avoids showing outdated vulnerable dependencies
  dependencyOverrides := {
    if (scalaVer.startsWith("3.")) Seq("org.scala-lang" % "scala-library" % Versions.scalaVersion)
    else Seq.empty
  },
  (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
  (Compile / scalacOptions) := Seq("-release", "8"),
  packageMethod := PackagingMethod.MergeIntoOther(worksheetReplInterfaceImpls),
  intellijMainJars := Seq.empty,
  intellijTestJars := Seq.empty,
  intellijPlugins := Seq.empty
)

lazy val worksheetReplInterfaceImpl_2_12: Project =
  newProject("worksheet-repl-interface-impl_2_12", file("scala/worksheet-repl-interface-impls/impl_2_12"))
    .dependsOn(worksheetReplInterface)
    .settings(
      worksheetReplInterfaceImplCommonSettings("2.12.12"),
      (Compile / scalacOptions) := Seq("-target:jvm-1.8") // Old version of Scala 2.12 does not have the modern compiler flags
    )
    .settings(
      libraryDependencies ++= Seq(
        compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.1" cross CrossVersion.full),
        "com.github.ghik" % "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
      ),
      (Compile / scalacOptions) += "-deprecation",
      // This is a workaround for manually enabling the `silencer-plugin` scalac compiler plugin. For some reason,
      // automatic enabling doesn't work (the scalacOption "-Xplugin:" was not added).
      // The silencer plugin is needed because this subproject is compiled using Scala 2.12.12 which did not have
      // support for `@scala.annotation.nowarn`.
      autoCompilerPlugins := false,
      ivyConfigurations += Configurations.CompilerPlugin,
      (Compile / scalacOptions) ++= Classpaths.autoPlugins(update.value, Seq.empty, isDotty = false)
    )

lazy val worksheetReplInterfaceImpl_2_12_13: Project =
  newProject("worksheet-repl-interface-impl_2_12_13", file("scala/worksheet-repl-interface-impls/impl_2_12_13"))
    .dependsOn(worksheetReplInterface)
    .settings(
      worksheetReplInterfaceImplCommonSettings("2.12.18"),
      (Compile / scalacOptions) += "-deprecation"
    )

lazy val worksheetReplInterfaceImpl_2_13_0: Project =
  newProject("worksheet-repl-interface-impl_2_13_0", file("scala/worksheet-repl-interface-impls/impl_2_13_0"))
    .dependsOn(worksheetReplInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("2.13.0"))

lazy val worksheetReplInterfaceImpl_2_13: Project =
  newProject("worksheet-repl-interface-impl_2_13", file("scala/worksheet-repl-interface-impls/impl_2_13"))
    .dependsOn(worksheetReplInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("2.13.11"))

lazy val worksheetReplInterfaceImpl_2_13_12: Project =
  newProject("worksheet-repl-interface-impl_2_13_12", file("scala/worksheet-repl-interface-impls/impl_2_13_12"))
    .dependsOn(worksheetReplInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("2.13.12"))

lazy val worksheetReplInterfaceImpl_3_0_0: Project =
  newProject("worksheet-repl-interface-impl_3_0_0", file("scala/worksheet-repl-interface-impls/impl_3_0_0"))
    .dependsOn(worksheetReplInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("3.1.1"))

lazy val worksheetReplInterfaceImpl_3_1_2: Project =
  newProject("worksheet-repl-interface-impl_3_1_2", file("scala/worksheet-repl-interface-impls/impl_3_1_2"))
    .dependsOn(worksheetReplInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("3.2.2"))

lazy val worksheetReplInterfaceImpl_3_3_0: Project =
  newProject("worksheet-repl-interface-impl_3_3_0", file("scala/worksheet-repl-interface-impls/impl_3_3_0"))
    .dependsOn(worksheetReplInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("3.3.1"))

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
    organization := "JetBrains",
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

lazy val packageSearchClient: sbt.Project =
  newProjectWithKotlin("package-search-client", file("scala/package-search-client"))
    .settings(
      scalaVersion := Versions.scala3Version,
      Compile / scalacOptions := globalScala3ScalacOptions,
      resolvers += DependencyResolvers.PackageSearch,
      libraryDependencies += Dependencies.packageSearchClientJvm,
    )

lazy val scalacPatches: sbt.Project =
  Project("scalac-patches", file("scalac-patches"))
    .settings(projectDirectoriesSettings)
    .settings(
      name := "scalac-patches",
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      libraryDependencies ++= Seq(Dependencies.scalaCompiler),
      packageMethod := PackagingMethod.Skip(),
      intellijMainJars := Nil,
      intellijTestJars := Nil
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
      packageSearchClient % "test->test;compile->compile",
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
        //for PlatformTestUtil.newPerformanceTest
        Dependencies.intellijIdeMetricsBenchmark % Test,
        Dependencies.intellijIdeMetricsCollector % Test,
        Dependencies.intellijIdeUtilCommon % Test,
      ),
      resolvers += Versions.intellijRepository_ForManagedIntellijDependencies,
      intellijPlugins += "JUnit".toPlugin,
      intellijPluginJars := intellijPluginJars.value.map { case PluginJars(descriptor, root, cp) =>
        PluginJars(descriptor, root, cp.filterNot(_.getName.contains("junit-jupiter-api")))
      },
      packageLibraryMappings := Seq(
        // "com.thesamet.scalapb" %% "scalapb-runtime" % ".*" -> None,
        // "com.thesamet.scalapb" %% "lenses" % ".*"          -> None,
        Dependencies.scalaXml                              -> Some("lib/scala-xml.jar"),
        Dependencies.scalaReflect                          -> Some("lib/scala-reflect.jar"),
        Dependencies.scalaLibrary                          -> None,
        Dependencies.scala3Library                         -> None,
      )
    )
    .withCompilerPluginIn(scalacPatches) // TODO Add to other modules

/**
 * Utilities which only depend on the Scala standard library and do not depend on other libraries or IntelliJ SDK
 */
lazy val scalaLanguageUtils: sbt.Project =
  newPlainScalaProject("scala-utils-language", file("scala/scala-utils-language"))
    .settings(
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity)
    )

/**
 * Same as [[scalaLanguageUtils]], but utilities from this module can be used form both IntelliJ IDEA process and JPS process.
 * Keep this module as small as possible with no other dependencies
 */
lazy val scalaLanguageUtilsRt: sbt.Project =
  newPlainScalaProject("scala-utils-language-rt", file("scala/scala-utils-language-rt"))
    .settings(
      Compile / javacOptions := outOfIDEAProcessJavacOptions,
      Compile / scalacOptions := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/utils_rt.jar", static = true),
    )

lazy val sbtImpl =
  newProject("sbt-impl", file("sbt/sbt-impl"))
    .dependsOn(sbtApi, scalaImpl % "test->test;compile->compile")
    .settings(
      intellijPlugins += "org.jetbrains.idea.maven".toPlugin
    )
    .withCompilerPluginIn(scalacPatches)

lazy val compilerIntegration =
  newProject("compiler-integration", file("scala/compiler-integration"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      sbtImpl % "test->test;compile->compile",
      jps,
      bsp
    )
    .settings(
      intellijPlugins ++= Seq(
        "com.intellij.gradle",
        "org.jetbrains.idea.maven"
      ).map(_.toPlugin), // Used only in tests
      libraryDependencies += Dependencies.intellijMavenTestFramework % Test
    )
    .withCompilerPluginIn(scalacPatches)

lazy val debugger =
  newProject("debugger", file("scala/debugger"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      compilerIntegration % "test->test;compile->compile"
    )
    .withCompilerPluginIn(scalacPatches)


lazy val compileServer =
  newPlainScalaProject("compile-server", file("scala/compile-server"))
    .dependsOn(compilerShared, repackagedZinc, worksheetReplInterface)
    .settings(
      Compile / javacOptions := outOfIDEAProcessJavacOptions,
      Compile / scalacOptions := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/jps/compile-server.jar", static = true),
      libraryDependencies += Dependencies.nailgun,
      packageLibraryMappings += Dependencies.nailgun -> Some("lib/jps/nailgun.jar"),
      //Manually build classpath for the jps module because the code from this module
      //will be executed in JPS process which has a separate classpath.
      //NOTE: this classpath is only required to properly compile the module
      //(in order we do not accidentally use any classes which are not available in JPS process)<br>
      //At runtime the classpath will be constructed in by Platform.
      Compile / unmanagedJars ++= Common.jpsClasspath.value
    )

lazy val compilerJps =
  newPlainScalaProject("compiler-jps", file("scala/compiler-jps"))
    .dependsOn(jps, compileServer)
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/jps/compiler-jps.jar", static = true),
      libraryDependencies ++= Seq(Dependencies.scalaParallelCollections),
      packageLibraryMappings ++= Seq(
        Dependencies.scalaParallelCollections -> Some("lib/jps/scala-parallel-collections.jar")
      ),
      //Manually build classpath for the jps module because the code from this module
      //will be executed in JPS process which has a separate classpath.
      //NOTE: this classpath is only required to properly compile the module
      //(in order we do not accidentally use any classes which are not available in JPS process)<br>
      //At runtime the classpath will be constructed in by Platform.
      Compile / unmanagedJars ++= Common.jpsClasspath.value
    )

lazy val repackagedZinc =
  newProject("repackagedZinc", file("target/tools/zinc"))
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
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/compiler-shared.jar", static = true),
      Compile / unmanagedJars ++= Common.compilerSharedClasspath.value
    )

lazy val jps =
  newPlainScalaProject("jps", file("scala/jps"))
    .settings(
      Compile / javacOptions := outOfIDEAProcessJavacOptions,
      Compile / scalacOptions := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/scala-jps.jar", static = true),
      libraryDependencies += Dependencies.compilerIndicesProtocol,
      packageLibraryMappings += Dependencies.compilerIndicesProtocol -> Some(s"lib/scala-compiler-indices-protocol_2.13-${Versions.compilerIndicesVersion}.jar"),
      //Manually build classpath for the jps module because the code from this module
      //will be executed in JPS process which has a separate classpath.
      //NOTE: this classpath is only required to properly compile the module
      //(in order we do not accidentally use any classes which are not available in JPS process)<br>
      //At runtime the classpath will be constructed in by Platform.
      Compile / unmanagedJars ++= Common.jpsClasspath.value
    )

lazy val runners: Project =
  newProject("runners", file("scala/runners"))
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone(static = true),
      packageAdditionalProjects ++= Seq(testRunners, testRunners_spec2_2x)
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
      intellijPlugins += "JUnit".toPlugin
    )
    .withCompilerPluginIn(scalacPatches)

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
    organization := "JetBrains",
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
    organization := "JetBrains",
    scalatestFindersTestSettings,
    scalaVersion := "2.11.12",
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_2 % Test),
    intellijMainJars := Nil,
    intellijTestJars := Nil
  )

lazy val scalatestFindersTests_3_0 = Project("scalatest-finders-tests-3_0", scalatestFindersRootDir / "tests-3_0")
  .dependsOn(scalatestFinders)
  .settings(
    name := "scalatest-finders-tests-3_0",
    organization := "JetBrains",
    scalatestFindersTestSettings,
    scalaVersion := "2.12.17",
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_3_0 % Test),
    intellijMainJars := Nil,
    intellijTestJars := Nil,
  )

lazy val scalatestFindersTests_3_2 = Project("scalatest-finders-tests-3_2", scalatestFindersRootDir / "tests-3_2")
  .dependsOn(scalatestFinders)
  .settings(
    name := "scalatest-finders-tests-3_2",
    organization := "JetBrains",
    scalatestFindersTestSettings,
    scalaVersion := Versions.scalaVersion,
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_3_2 % Test),
    intellijMainJars := Nil,
    intellijTestJars := Nil,
  )

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

lazy val bsp =
  newProject("bsp", file("bsp"))
    .enablePlugins(BuildInfoPlugin)
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      sbtImpl % "test->test;compile->compile"
    )
    .settings(
      libraryDependencies ++= DependencyGroups.bsp,
      intellijPlugins += "JUnit".toPlugin,
      intellijPlugins += "org.jetbrains.plugins.terminal".toPlugin,
      buildInfoPackage := "org.jetbrains.bsp.buildinfo",
      buildInfoKeys := Seq("bloopVersion" -> Versions.bloopVersion),
      buildInfoOptions += BuildInfoOption.ConstantValue
    )

// Integration with other IDEA plugins
//TODO: rename the module module and maybe base packages (check external usages)
// it actually doesn't have anything related to actual devkit integration, it doesn't depend on anything from it
// it's similar to DevKit plugin in it's purpose, but it's different.
// It just contains some internal actions required for Scala Plugin development (or other Scala-based plugins using sbt-idea-plugin)
lazy val devKitIntegration =
  newProject("devKit", file("scala/integration/devKit"))
    .dependsOn(scalaImpl, sbtImpl)

lazy val copyrightIntegration =
  newProject("copyright", file("scala/integration/copyright"))
    .dependsOn(scalaImpl)
    .settings(
      intellijPlugins += "com.intellij.copyright".toPlugin,
      packageMethod := PackagingMethod.MergeIntoOther(scalaCommunity)
    )

lazy val gradleIntegration =
  newProject("gradle", file("scala/integration/gradle"))
    .dependsOn(scalaImpl % "test->test;compile->compile", sbtImpl % "test->test")
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
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      testingSupport,
      sbtImpl % "test->test"
    )
    .settings(
      intellijPlugins += "org.jetbrains.idea.maven".toPlugin,
      libraryDependencies += Dependencies.intellijMavenTestFramework % Test,
      resolvers += Versions.intellijRepository_ForManagedIntellijDependencies
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
    .dependsOn(scalaImpl)
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

//Integration with:
// - Build-in spellchecker (see com.intellij.spellchecker package)
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
      intellijPlugins ++= Seq(
        "tanvd.grazi".toPlugin,
        "org.intellij.intelliLang".toPlugin //required for intelliLangIntegration
      ),
      //Language packs needed at runtime to run tests
      libraryDependencies ++= Seq(
        //languagetool-core is available in the platform, exclude it to avoid some strange runtime errors in tests
        ("org.languagetool" % "language-ru" % Versions.LanguageToolVersion % Runtime).exclude("org.languagetool", "languagetool-core"),
        ("org.languagetool" % "language-de" % Versions.LanguageToolVersion % Runtime).exclude("org.languagetool", "languagetool-core"),
        ("org.languagetool" % "language-it" % Versions.LanguageToolVersion % Runtime).exclude("org.languagetool", "languagetool-core"),
      )
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
  .settings(
    name := "runtimeDependencies",
    scalaVersion := Versions.scalaVersion,
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
      sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.binary_0_13),
      sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.structure_extractor_binary_1_2),
      sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.structure_extractor_binary_1_3),

      sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_0_13),
      sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_1_0)

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

lazy val runTestCategory = Command.single("runTestCategory") { (state, category) =>
  val state1 = Command.process(
    s"""set Seq(
       |  Test / testFrameworks := Seq(TestFrameworks.JUnit),
       |  Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-v", "-s", "-a", "+c", "+q", "--include-categories=$category", "--exclude-categories=$flakyTests"))
       |)""".stripMargin,
    state,
    _ => ()
  )
  Command.process("testOnly", state1, _ => ())
  state
}

def runTestsInTC(category: String): String = s"runTestCategory $category"

addCommandAlias("runFileSetTests", runTestsInTC(fileSetTests))
addCommandAlias("runCompilationTests", runTestsInTC(compilationTests))
addCommandAlias("runCompletionTests", runTestsInTC(completionTests))
addCommandAlias("runEditorTests", runTestsInTC(editorTests))
addCommandAlias("runSlowTests", runTestsInTC(slowTests))
addCommandAlias("runDebuggerTests", runTestsInTC(debuggerTests))
addCommandAlias("runScalacTests", runTestsInTC(scalacTests))
addCommandAlias("runTypeInferenceTests", runTestsInTC(typecheckerTests))
addCommandAlias("runTestingSupportTests", runTestsInTC(testingSupportTests))
addCommandAlias("runWorksheetEvaluationTests", runTestsInTC(worksheetEvaluationTests))
addCommandAlias("runHighlightingTests", runTestsInTC(highlightingTests))
addCommandAlias("runNightlyTests", runTestsInTC(randomTypingTests))

lazy val runFlakyTests = Command.command("runFlakyTests") { state =>
  val state1 = Command.process(
    s"""set Seq(
       |  Test / testFrameworks := Seq(TestFrameworks.JUnit),
       |  Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-v", "-s", "-a", "+c", "+q", "--include-categories=$flakyTests"))
       |)""".stripMargin,
    state,
    _ => ()
  )
  Command.process("testOnly", state1, _ => ())
  state
}

//it's run during "Package" step on TC
addCommandAlias("runBundleSortingTests", runTestsInTC(bundleSortingTests))

lazy val runFastTestsCommand = Command.single("runFastTestsCommand") { (state, glob) =>
  val categoriesToExclude = List(
    fileSetTests,
    compilationTests,
    completionTests,
    editorTests,
    slowTests,
    debuggerTests,
    scalacTests,
    typecheckerTests,
    testingSupportTests,
    highlightingTests,
    worksheetEvaluationTests,
    randomTypingTests,
    flakyTests
  )
  val state1 = Command.process(
    s"""set Seq(
       |  Test / testFrameworks := Seq(TestFrameworks.JUnit),
       |  Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-v", "-s", "-a", "+c", "+q", "--exclude-categories=${categoriesToExclude.mkString(",")}"))
       |)""".stripMargin,
    state,
    _ => ()
  )
  Command.process(s"testOnly $glob", state1, _ => ())
  state
}

addCommandAlias("runFastTests", "runFastTestsCommand *")
// subsets of tests to split the complete test run into smaller chunks
addCommandAlias("runFastTestsComIntelliJ", "runFastTestsCommand com.intellij.*")
addCommandAlias("runFastTestsOrgJetbrains", "runFastTestsCommand org.jetbrains.*")
addCommandAlias("runFastTestsScala", "runFastTestsCommand scala.*")

lazy val runJUnit5Tests = Command.command("runJUnit5Tests") { state =>
  val testFrameworkFqn = "com.github.sbt.junit.jupiter.sbt.Import.jupiterTestFramework"
  val state1 = Command.process(
    s"""set Seq(
       |  Test / testFrameworks := Seq($testFrameworkFqn),
       |  Test / testOptions := Seq(Tests.Argument($testFrameworkFqn, "-v", "-s", "-a", "+c", "+q", "--display-mode=tree"))
       |)""".stripMargin,
    state,
    _ => ()
  )
  Command.process("test", state1, _ => ())
  state
}

Global / commands ++= Seq(runTestCategory, runFlakyTests, runFastTestsCommand, runJUnit5Tests)
