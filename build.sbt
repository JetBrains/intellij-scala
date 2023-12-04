import Common.*
import Dependencies.provided
import DynamicDependenciesFetcher.*
import LocalRepoPackager.{localRepoDependencies, localRepoUpdate, relativeJarPath, sbtDep}
import org.jetbrains.sbtidea.Keys.*

import java.nio.file.Path

// Global build settings

(ThisBuild / intellijPluginName) := "Scala"

(ThisBuild / intellijBuild) := Versions.intellijVersion

(ThisBuild / intellijPlatform) := (Global / intellijPlatform).??(IntelliJPlatform.IdeaCommunity).value

(ThisBuild / resolvers) ++=
  Resolver.sonatypeOssRepos("releases") ++
    Resolver.sonatypeOssRepos("staging") ++
    Resolver.sonatypeOssRepos("snapshots") :+
    ("scala-integration" at "https://scala-ci.typesafe.com/artifactory/scala-integration/")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

(Global / javacOptions) := globalJavacOptions

(Global / scalacOptions) := globalScalacOptions

//todo remove after fixing leak in sbt.internal.inc.HashUtil.farmHash
//UPD: this is probably about: https://github.com/sbt/sbt/issues/6029
Global / concurrentRestrictions := Seq(Tags.limitAll(3))

val intellijPluginsScopeFilter: ScopeFilter =
  ScopeFilter(inDependencies(ThisProject, includeRoot = false))

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
      intellijBspIntegration % "test->test;compile->compile",
      intelliLangIntegration % "test->test;compile->compile",
      mavenIntegration % "test->test;compile->compile",
      propertiesIntegration % "test->test;compile->compile",
      mlCompletionIntegration % "test->test;compile->compile",
      pluginXml,
    )
    .settings(
      ideExcludedDirectories    := Seq(baseDirectory.value / "target", baseDirectory.value / "compilation-cache"),
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
      packageLibraryMappings := Dependencies.scalaLibrary -> Some("lib/scala-library.jar") :: Nil,
      packageMethod := PackagingMethod.Standalone(),
      intellijPlugins := intellijPlugins.all(intellijPluginsScopeFilter).value.flatten.distinct ++ Seq(
        //Below are some other useful plugins which you might be interested to inspect
        //We don't have any dependencies on those plugins, however sometimes it might be useful to see how some features are implemented in them plugin.
        //You can uncomment any of them locally

        //This bundled plugin contains some internal development tools such as "View Psi Structure" action
        //(note there is also PsiViewer plugin, but it's a different plugin)
        //"com.intellij.dev".toPlugin,

        //"org.jetbrains.kotlin".toPlugin
      ),
      // all sub-project tests need to be run within main project's classpath
      Test / definedTests := definedTests.all(definedTestsScopeFilter).value.flatten
    )

lazy val pluginXml = newProject("pluginXml", file("pluginXml"))
  .settings(
    packageMethod := PackagingMethod.Standalone(),
  )

lazy val scalaApi = newProject(
  "scala-api",
  file("scala/scala-api")
).settings(
      idePackagePrefix := Some("org.jetbrains.plugins.scala"),
    )

lazy val sbtApi =
  newProject("sbt-api", file("sbt/sbt-api"))
    .dependsOn(scalaApi, compilerShared)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      ideExcludedDirectories := Seq(baseDirectory.value / "target"),
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

lazy val uast = newProject(
  "uast",
  file("scala/uast")
).dependsOn(
  scalaImpl % "test->test;compile->compile"
)

lazy val worksheet =
  newProject("worksheet", file("scala/worksheet"))
    .dependsOn(
      compilerIntegration % "test->test;compile->compile",
      worksheetReplInterface % "test->test;compile->compile"
    )

lazy val worksheetReplInterface =
  Project("worksheet-repl-interface", file("scala/worksheet-repl-interface"))
    .settings(
      name := "worksheet-repl-interface",
      organization := "JetBrains",
      (Compile / unmanagedSourceDirectories) += baseDirectory.value / "src",
      // NOTE: we might continue NOT using Scala in scalatestFinders just in case
      // in some future we will decide again to extract the library, so as it can be used even without scala jar
      scalaVersion := Versions.scalaVersion,
      crossPaths := false, // disable using the Scala version in output paths and artifacts
      autoScalaLibrary := false, // removes Scala dependency
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions, // can run in the compile server
      (Compile / scalacOptions) := Seq.empty, // scala is disabled anyway, set empty options to move to a separate compiler profile (in IntelliJ model)
      packageMethod :=  PackagingMethod.Standalone("lib/repl-interface.jar", static = true),
      intellijMainJars := Seq.empty,
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
    .settings(worksheetReplInterfaceImplCommonSettings("3.3.0"))

lazy val structureView = newProject("structure-view", file("scala/structure-view"))
  .dependsOn(scalaImpl % "test->test;compile->compile")
  .withCompilerPluginIn(scalacPatches)
  .settings(
    scalaVersion := Versions.scala3Version,
    Compile / scalacOptions := globalScala3ScalacOptions,
  )

lazy val tastyReader = Project("tasty-reader", file("scala/tasty-reader"))
  .dependsOn(scalaLanguageUtils)
  .settings(
    name := "tasty-reader",
    organization := "JetBrains",
    idePackagePrefix := Some("org.jetbrains.plugins.scala.tasty.reader"),
    intellijMainJars := Seq.empty,
    scalaVersion := Versions.scala3Version,
    libraryDependencies += "org.scala-lang" % "tasty-core_3" % Versions.scala3Version,
    (Compile / scalacOptions) := Seq("-deprecation"),
    (Compile / unmanagedSourceDirectories) += baseDirectory.value / "src",
    (Test / unmanagedSourceDirectories) += baseDirectory.value / "test",
    (Test / unmanagedResourceDirectories) += baseDirectory.value / "testdata",
    libraryDependencies ++= Seq(
      Dependencies.junit % Test,
      Dependencies.junitInterface % Test,
    )
  )

lazy val scalacPatches: sbt.Project =
  Project("scalac-patches", file("scalac-patches"))
    .settings(
      name := "scalac-patches",
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      Compile / unmanagedSourceDirectories += baseDirectory.value / "src",
      libraryDependencies ++= Seq(Dependencies.scalaCompiler),
      packageMethod := PackagingMethod.Skip(),
      intellijMainJars := Nil
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
      testRunners
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

      //for ExternalSystemTestCase and ExternalSystemImportingTestCase
      libraryDependencies += "com.jetbrains.intellij.platform" % "external-system-test-framework" % Versions.intellijVersion_ForManagedIntellijDependencies % Test notTransitive(),
      resolvers += Versions.intellijRepository_ForManagedIntellijDependencies,
      intellijPlugins += "JUnit".toPlugin,
      intellijPluginJars :=
        intellijPluginJars.value.map { case (descriptor, cp) => descriptor -> cp.filterNot(_.data.getName.contains("junit-jupiter-api")) },
      packageLibraryMappings := Seq(
        // "com.thesamet.scalapb" %% "scalapb-runtime" % ".*" -> None,
        // "com.thesamet.scalapb" %% "lenses" % ".*"          -> None,
        Dependencies.scalaXml                              -> Some("lib/scala-xml.jar"),
        Dependencies.scalaReflect                          -> Some("lib/scala-reflect.jar"),
        Dependencies.scalaLibrary                          -> None
      )
    )
    .withCompilerPluginIn(scalacPatches) // TODO Add to other modules

//The module is meant to keep utilities which only depend on Scala standard library
//and do not depend on other libraries or IntelliJ SDK
lazy val scalaLanguageUtils: sbt.Project =
  newPlainScalaProject("scala-utils-language", file("scala/scala-utils-language"))

//Same as scalaLanguageUtils, but utilities from this module can be used form both IntelliJ IDEA process and JPS process
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
      jps
    )
    .settings(
      intellijPlugins ++= Seq(
        "com.intellij.gradle",
        "org.jetbrains.idea.maven"
      ).map(_.toPlugin), // Used only in tests
      libraryDependencies += "com.jetbrains.intellij.maven" % "maven-test-framework" % Versions.intellijVersion_ForManagedIntellijDependencies % Test notTransitive()
    )
    .withCompilerPluginIn(scalacPatches)

lazy val debugger =
  newProject("debugger", file("scala/debugger"))
    .dependsOn(
      scalaImpl % "test->test;compile->compile",
      compilerIntegration
    )
    .withCompilerPluginIn(scalacPatches)

lazy val compileServer =
  newProject("compile-server", file("scala/compile-server"))
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
      intellijMainJars := Seq.empty,
      intellijPlugins := Seq.empty,
      Compile / unmanagedJars ++= Common.jpsClasspath.value
    )

lazy val compilerJps =
  newProject("compiler-jps", file("scala/compiler-jps"))
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
      intellijMainJars := Seq.empty,
      intellijPlugins := Seq.empty,
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
  newProject("compiler-shared", file("scala/compiler-shared"))
    .dependsOn(scalaLanguageUtilsRt)
    .settings(
      (Compile / javacOptions) := outOfIDEAProcessJavacOptions,
      (Compile / scalacOptions) := outOfIDEAProcessScalacOptions,
      packageMethod := PackagingMethod.Standalone("lib/compiler-shared.jar", static = true),
      intellijMainJars := Seq.empty,
      intellijPlugins := Seq.empty,
      Compile / unmanagedJars ++= Common.compilerSharedClasspath.value
    )

lazy val jps =
  newProject("jps", file("scala/jps"))
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
      intellijMainJars := Nil,
      intellijPlugins := Nil,
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
      structureView % "test->test;compile->compile",
      debugger % "test->test;compile->compile" // these modules share some test setup code, which should be refactored
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
    intellijMainJars := Nil //without this lineon SDK is still added (as "Provided"), while it shouldn't
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
    intellijMainJars := Nil
  )

lazy val scalatestFindersTests_3_0 = Project("scalatest-finders-tests-3_0", scalatestFindersRootDir / "tests-3_0")
  .dependsOn(scalatestFinders)
  .settings(
    name := "scalatest-finders-tests-3_0",
    organization := "JetBrains",
    scalatestFindersTestSettings,
    scalaVersion := "2.12.17",
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_3_0 % Test),
    intellijMainJars := Nil
  )

lazy val scalatestFindersTests_3_2 = Project("scalatest-finders-tests-3_2", scalatestFindersRootDir / "tests-3_2")
  .dependsOn(scalatestFinders)
  .settings(
    name := "scalatest-finders-tests-3_2",
    organization := "JetBrains",
    scalatestFindersTestSettings,
    scalaVersion := Versions.scalaVersion,
    libraryDependencies := Seq("org.scalatest" %% "scalatest" % scalatestLatest_3_2 % Test),
    intellijMainJars := Nil
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
      testingSupport,
      worksheet % "test->test;compile->compile"
    )
    .settings(
      libraryDependencies ++= DependencyGroups.bsp,
      intellijPlugins += "JUnit".toPlugin,
      intellijPlugins += "org.jetbrains.plugins.terminal".toPlugin,
      buildInfoPackage := "org.jetbrains.bsp.buildinfo",
      buildInfoKeys := Seq("bloopVersion" -> Versions.bloopVersion),
      buildInfoOptions += BuildInfoOption.ConstantValue,
      ideExcludedDirectories := Seq(baseDirectory.value / "target")
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

lazy val intellijBspIntegration =
  newProject("intellij-bsp", file("scala/integration/intellij-bsp"))
    .dependsOn(scalaImpl)
    .settings(
      intellijPlugins += "org.jetbrains.bsp::nightly".toPlugin
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
      libraryDependencies += "com.jetbrains.intellij.maven" % "maven-test-framework" % Versions.intellijVersion_ForManagedIntellijDependencies % Test notTransitive(),
      resolvers += Versions.intellijRepository_ForManagedIntellijDependencies
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
      resolvers += "intellij-dependencies" at "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/",
      libraryDependencies += "org.jetbrains.intellij.deps.completion" % "completion-ranking-scala" % "0.4.1"
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
      binaryDep("org.scala-lang", "scala3-sbt-bridge", "3.3.1") -> "lib/jps/scala3-sbt-bridge_3.3.jar",

      binaryDep("org.scala-sbt.rt", "java9-rt-export", Versions.java9rtExportVersion) -> "java9-rt-export/java9-rt-export.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.3.1", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.3.1.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.3.0", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.3.0.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.2.2", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.2.2.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.2.1", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.2.1.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.2.0", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.2.0.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.1.3", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.1.3.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.1.2", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.1.2.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.1.1", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.1.1.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.1.0", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.1.0.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.0.2", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.0.2.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.0.1", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.0.1.jar",
      binaryDep("ch.epfl.scala", "scala-expression-compiler", "3.0.0", Versions.scalaExpressionCompiler) -> "debugger/scala-expression-compiler_3.0.0.jar",
    ),
    localRepoDependencies := List(
      sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.binary_0_13),
      sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.structure_extractor_binary_1_2),
      sbtDep("org.jetbrains.scala", "sbt-structure-extractor", Versions.sbtStructureVersion, Versions.Sbt.structure_extractor_binary_1_3),

      sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_0_13),
      sbtDep("org.jetbrains.scala", "sbt-idea-shell", Versions.sbtIdeaShellVersion, Versions.Sbt.binary_1_0),

      sbtDep("org.jetbrains.scala", "sbt-idea-compiler-indices", Versions.compilerIndicesVersion, Versions.Sbt.binary_0_13),
      sbtDep("org.jetbrains.scala", "sbt-idea-compiler-indices", Versions.compilerIndicesVersion, Versions.Sbt.binary_1_0)
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

// Testing keys and settings
import Common.TestCategory.*

val junitInterfaceFlags = "-v -s -a +c +q"

def testOnlyCategories(categories: String*): String =
  s"testOnly -- $junitInterfaceFlags --include-categories=${categories.mkString(",")} --exclude-categories=$flakyTests"

addCommandAlias("runFileSetTests", testOnlyCategories(fileSetTests))
addCommandAlias("runCompilationTests", testOnlyCategories(compilationTests))
addCommandAlias("runCompletionTests", testOnlyCategories(completionTests))
addCommandAlias("runEditorTests", testOnlyCategories(editorTests))
addCommandAlias("runSlowTests", testOnlyCategories(slowTests))
addCommandAlias("runDebuggerTests", testOnlyCategories(debuggerTests))
addCommandAlias("runScalacTests", testOnlyCategories(scalacTests))
addCommandAlias("runTypeInferenceTests", testOnlyCategories(typecheckerTests))
addCommandAlias("runTestingSupportTests", testOnlyCategories(testingSupportTests))
addCommandAlias("runWorksheetEvaluationTests", testOnlyCategories(worksheetEvaluationTests))
addCommandAlias("runHighlightingTests", testOnlyCategories(highlightingTests))
addCommandAlias("runNightlyTests", testOnlyCategories(randomTypingTests))
addCommandAlias("runFlakyTests", s"testOnly -- --include-categories=$flakyTests")

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

val fastTestOptions = s"$junitInterfaceFlags --exclude-categories=${categoriesToExclude.mkString(",")}"

addCommandAlias("runFastTests", s"testOnly -- $fastTestOptions")
// subsets of tests to split the complete test run into smaller chunks
addCommandAlias("runFastTestsComIntelliJ", s"testOnly com.intellij.* -- $fastTestOptions")
addCommandAlias("runFastTestsOrgJetbrains", s"testOnly org.jetbrains.* -- $fastTestOptions")
addCommandAlias("runFastTestsScala", s"testOnly scala.* -- $fastTestOptions")
