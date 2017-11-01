import java.io.File

import Common._
import com.dancingrobot84.sbtidea.tasks.{UpdateIdea => updateIdeaTask}
import sbt.Keys.{`package` => pack}
import sbtide.Keys.ideSkipProject

// Global build settings

resolvers in ThisBuild ++=
  BintrayJetbrains.allResolvers :+
  Resolver.typesafeIvyRepo("releases")

resolvers in ThisBuild += Resolver.sonatypeRepo("snapshots")

ideaBuild in ThisBuild := Versions.ideaVersion

ideaDownloadDirectory in ThisBuild := homePrefix / ".ScalaPluginIC" / "sdk"

testConfigDir in ThisBuild := homePrefix / ".ScalaPluginIC" / "test-config"

testSystemDir in ThisBuild := homePrefix / ".ScalaPluginIC" / "test-system"

onLoad in Global := ((s: State) => { "updateIdea" :: s}) compose (onLoad in Global).value

addCommandAlias("downloadIdea", "updateIdea")

addCommandAlias("packagePluginCommunity", "pluginPackagerCommunity/package")

addCommandAlias("packagePluginCommunityZip", "pluginCompressorCommunity/package")

// Main projects
lazy val scalaCommunity: sbt.Project =
  newProject("scalaCommunity", file("."))
    .dependsOn(scalaImpl % "test->test;compile->compile", cbt % "test->test;compile->compile")
    .aggregate(scalaImpl, cbt)
    .settings(
      aggregate.in(updateIdea) := false,
      ideExcludedDirectories := Seq(baseDirectory.value / "target")
    )

lazy val scalaImpl: sbt.Project =
  newProject("scala-impl", file("scala/scala-impl"))
    .dependsOn(compilerShared, decompiler % "test->test;compile->compile", runners % "test->test;compile->compile", macroAnnotations)
  .enablePlugins(SbtIdeaPlugin, BuildInfoPlugin)
  .settings(commonTestSettings(packagedPluginDir):_*)
  .settings(
    ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects"),
    javacOptions in Global ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions in Global ++= Seq("-target:jvm-1.8", "-deprecation"),
    //scalacOptions in Global += "-Xmacro-settings:analyze-caches",
    libraryDependencies ++= DependencyGroups.scalaCommunity,
    unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
    addCompilerPlugin(Dependencies.macroParadise),
    ideaInternalPlugins := Seq(
      "copyright",
      "gradle",
      "Groovy",
      "IntelliLang",
      "java-i18n",
      "android",
      "maven",
      "junit",
      "properties"
    ),
    ideaInternalPluginsJars :=
      ideaInternalPluginsJars.value.filterNot(cp => cp.data.getName.contains("junit-jupiter-api"))
    ,
    Keys.aggregate.in(updateIdea) := false,
    test in Test := test.in(Test).dependsOn(setUpTestEnvironment).value,
    testOnly in Test := testOnly.in(Test).dependsOn(setUpTestEnvironment).evaluated,
    buildInfoPackage := "org.jetbrains.plugins.scala.buildinfo",
    buildInfoKeys := Seq(
      name, version, scalaVersion, sbtVersion,
      BuildInfoKey.constant("sbtLatestVersion", Versions.sbtVersion),
      BuildInfoKey.constant("sbtStructureVersion", Versions.sbtStructureVersion),
      BuildInfoKey.constant("sbtIdeaShellVersion", Versions.sbtIdeaShellVersion),
      BuildInfoKey.constant("sbtLatest_0_13", Versions.Sbt.latest_0_13)
    ),
    fullClasspath in Test := deduplicatedClasspath((fullClasspath in Test).value, communityFullClasspath.value)
  )

lazy val compilerJps =
  newProject("compiler-jps", file("scala/compiler-jps"))
  .dependsOn(compilerShared)
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    libraryDependencies ++=
      Seq(Dependencies.nailgun) ++
        DependencyGroups.sbtBundled
  )

lazy val compilerShared =
  newProject("compiler-shared", file("scala/compiler-shared"))
  .enablePlugins(SbtIdeaPlugin)
  .settings(libraryDependencies += Dependencies.nailgun)

lazy val runners =
  newProject("runners", file("scala/runners"))
  .settings(
    libraryDependencies ++= DependencyGroups.runners,
    // WORKAROUND fixes build error in sbt 0.13.12+ analogously to https://github.com/scala/scala/pull/5386/
    ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))
  )

lazy val nailgunRunners =
  newProject("nailgun", file("scala/nailgun"))
  .dependsOn(runners)
  .settings(libraryDependencies += Dependencies.nailgun)

lazy val decompiler =
  newProject("decompiler", file("scala/decompiler"))
    .settings(commonTestSettings(packagedPluginDir):_*)
    .settings(libraryDependencies ++= DependencyGroups.decompiler)

lazy val macroAnnotations =
  newProject("macros", file("scala/macros"))
  .settings(Seq(
    addCompilerPlugin(Dependencies.macroParadise),
    libraryDependencies ++= Seq(Dependencies.scalaReflect, Dependencies.scalaCompiler)
  ): _*)

lazy val cbt =
  newProject("cbt", file("cbt"))
    .enablePlugins(SbtIdeaPlugin)
    .dependsOn(scalaImpl % "test->test;compile->compile")

// Utility projects

lazy val ideaRunner =
  newProject("idea-runner", file("target/tools/idea-runner"))
  .dependsOn(Seq(compilerShared, runners, scalaCommunity, compilerJps, nailgunRunners, decompiler).map(_ % Provided): _*)
  .settings(
    autoScalaLibrary := false,
    unmanagedJars in Compile := ideaMainJars.in(scalaImpl).value,
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
    // run configuration
    fork in run := true,
    mainClass in (Compile, run) := Some("com.intellij.idea.Main"),
    javaOptions in run ++= Seq(
      "-Xmx800m",
      "-XX:ReservedCodeCacheSize=64m",
      "-XX:MaxPermSize=250m",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-ea",
      "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005",
      "-Didea.is.internal=true",
      "-Didea.debug.mode=true",
      s"-Didea.system.path=${System.getProperty("user.home")}/.ScalaPluginIC/system",
      s"-Didea.config.path=${System.getProperty("user.home")}/.ScalaPluginIC/config",
      "-Dapple.laf.useScreenMenuBar=true",
      s"-Dplugin.path=${packagedPluginDir.value}",
      "-Didea.ProcessCanceledException=disabled"
    ),
    products in Compile := {
      (products in Compile).value :+ (pack in pluginPackagerCommunity).value
    }
  )

lazy val sbtRuntimeDependencies =
  (project in file("target/tools/sbt-runtime-dependencies"))
  .settings(
    libraryDependencies := DependencyGroups.sbtRuntime,
    managedScalaInstance := false,
    conflictManager := ConflictManager.all,
    conflictWarning := ConflictWarning.disable,
    resolvers += sbt.Classpaths.sbtPluginReleases,
    ideSkipProject := true
  )

lazy val testDownloader =
  newProject("testJarsDownloader", file("target/tools/test-jars-downloader"))
  .settings(
    conflictManager := ConflictManager.all,
    conflictWarning := ConflictWarning.disable,
    resolvers ++= Seq(
      "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
    ),
    libraryDependencies ++= DependencyGroups.testDownloader,
    libraryDependencies ++= DependencyGroups.mockSbtDownloader,
    libraryDependencies ++= DependencyGroups.testScalaLibraryDownloader,
    dependencyOverrides ++= Set(
      "com.chuusai" % "shapeless_2.11" % "2.0.0"
    ),
    update := update.dependsOn(update.in(sbtLaunchTestDownloader)).value,
    ideSkipProject := true
  )

lazy val sbtLaunchTestDownloader =
  newProject("sbtLaunchTestDownloader", file("target/tools/sbt-launch-test-downloader"))
  .settings(
    autoScalaLibrary := false,
    conflictManager := ConflictManager.all,
    libraryDependencies ++= DependencyGroups.sbtLaunchTestDownloader,
    ideSkipProject := true
  )

//lazy val jmhBenchmarks =
//  newProject("benchmarks", file("scala/benchmarks"))
//    .dependsOn(scalaImpl % "test->test")
//    .enablePlugins(JmhPlugin)

// Testing keys and settings
import Common.TestCategory._

addCommandAlias("runPerfOptTests", s"testOnly -- --include-categories=$perfOptTests")
addCommandAlias("runSlowTests", s"testOnly -- --include-categories=$slowTests")
addCommandAlias("runHighlightingTests", s"testOnly -- --include-categories=$highlightingTests")
addCommandAlias("runScalacTests", s"testOnly -- --include-categories=$scalacTests")
addCommandAlias("runTypeInferenceTests", s"testOnly org.jetbrains.plugins.scala.lang.typeInference.*")

val fastTestOptions = "-v -s -a +c +q " +
  s"--exclude-categories=$slowTests " +
  s"--exclude-categories=$perfOptTests " +
  s"--exclude-categories=$scalacTests " +
  s"--exclude-categories=$highlightingTests"

addCommandAlias("runFastTests", s"testOnly -- $fastTestOptions")
// subsets of tests to split the complete test run into smaller chunks
addCommandAlias("runFastTestsComIntelliJ", s"testOnly com.intellij.* -- $fastTestOptions")
addCommandAlias("runFastTestsOrgJetbrains", s"testOnly org.jetbrains.* -- $fastTestOptions")
addCommandAlias("runFastTestsScala", s"testOnly scala.* -- $fastTestOptions")


lazy val setUpTestEnvironment = taskKey[Unit]("Set up proper environment for running tests")

setUpTestEnvironment in ThisBuild := {
  update.in(testDownloader).value
}

lazy val cleanUpTestEnvironment = taskKey[Unit]("Clean up IDEA test system and config directories")

cleanUpTestEnvironment in ThisBuild := {
  IO.delete(testSystemDir.value)
  IO.delete(testConfigDir.value)
}

concurrentRestrictions in Global := Seq(
  Tags.limit(Tags.Test, 1)
)

communityFullClasspath in ThisBuild :=
  deduplicatedClasspath(fullClasspath.in(scalaCommunity, Test).value, fullClasspath.in(scalaCommunity, Compile).value)

// Packaging projects

lazy val packagedPluginDir = settingKey[File]("Path to packaged, but not yet compressed plugin")

packagedPluginDir in ThisBuild := baseDirectory.in(ThisBuild).value / "target" / "plugin" / "Scala"

lazy val iLoopWrapperPath = settingKey[File]("Path to repl interface sources")

iLoopWrapperPath := baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapperImpl.scala"

//packages output of several modules to a single jar
lazy val scalaPluginJarPackager =
  newProject("scalaPluginJarPackager", file("target/tools/scalaPluginJarPackager"))
    .settings(
      products in Compile :=
        products.in(scalaImpl, Compile).value ++
        products.in(cbt, Compile).value,
      ideSkipProject := true
    )

lazy val pluginPackagerCommunity =
  newProject("pluginPackagerCommunity", file("target/tools/packager"))
  .settings(
    artifactPath := packagedPluginDir.value,
    dependencyClasspath :=
      dependencyClasspath.in(scalaCommunity, Compile).value ++
      dependencyClasspath.in(compilerJps, Compile).value ++
      dependencyClasspath.in(runners, Compile).value ++
      dependencyClasspath.in(sbtRuntimeDependencies, Compile).value
    ,
    mappings := {
      import Dependencies._
      import Packaging.PackageEntry._

      val crossLibraries = (
        List(Dependencies.scalaParserCombinators, Dependencies.scalaXml) ++
          DependencyGroups.scalaCommunity
        ).distinct
      val jps = Seq(
        Artifact(pack.in(compilerJps, Compile).value,
          "lib/jps/compiler-jps.jar"),
        Library(nailgun,
          "lib/jps/nailgun.jar"),
        Library(Dependencies.compilerBridgeSources_2_10,
          "lib/jps/compiler-interface-sources-2.10.jar"),
        Library(Dependencies.compilerBridgeSources_2_11,
          "lib/jps/compiler-interface-sources-2.11.jar"),
        Artifact((assembly in repackagedZinc).value,
          "lib/jps/incremental-compiler.jar"),
        Library(Dependencies.zincInterface,
          "lib/jps/compiler-interface.jar"),
        Library(sbtInterface,
          "lib/jps/sbt-interface.jar"),
        Artifact(Packaging.putInTempJar(baseDirectory.in(compilerJps).value / "resources" / "ILoopWrapperImpl.scala" ),
          "lib/jps/repl-interface-sources.jar")
      )
      val launcher = Seq(
        Library(sbtStructureExtractor_012,
          "launcher/sbt-structure-0.12.jar"),
        Library(sbtStructureExtractor_013,
          "launcher/sbt-structure-0.13.jar"),
        Library(sbtStructureExtractor_100,
          "launcher/sbt-structure-1.0.jar"),
        Library(sbtLaunch,
          "launcher/sbt-launch.jar")
      )
      val lib = Seq(
        Artifact(pack.in(scalaPluginJarPackager, Compile).value,
          "lib/scala-plugin.jar"),
        Artifact(pack.in(decompiler, Compile).value,
          "lib/scalap.jar"),
        Artifact(pack.in(compilerShared, Compile).value,
          "lib/compiler-shared.jar"),
        Artifact(pack.in(nailgunRunners, Compile).value,
          "lib/scala-nailgun-runner.jar"),
        Artifact(pack.in(runners, Compile).value,
          "lib/runners.jar"),
        AllOrganisation("org.scalameta", "lib/scalameta120.jar"),
        Library(fastparse,
          "lib/fastparse.jar"),
        Library(scalaLibrary,
          "lib/scala-library.jar"),
        Library(bcel,
          "lib/bcel.jar")
      ) ++
        crossLibraries.map { lib =>
          Library(
            lib.copy(name = Packaging.crossName(lib, scalaVersion.value)),
            s"lib/${lib.name}.jar"
          )
        }

      Packaging.convertEntriesToMappings(
        jps ++ lib ++ launcher,
        dependencyClasspath.value
      )
    },
    pack := {
      Packaging.packagePlugin(mappings.value, artifactPath.value)
      artifactPath.value
    },
    ideSkipProject := true
  )


lazy val pluginCompressorCommunity =
  newProject("pluginCompressorCommunity", file("target/tools/compressor"))
  .settings(
    artifactPath := baseDirectory.in(ThisBuild).value / "target" / "scala-plugin.zip",
    pack := {
      Packaging.compressPackagedPlugin(pack.in(pluginPackagerCommunity).value, artifactPath.value)
      artifactPath.value
    },
    ideSkipProject := true
  )

lazy val repackagedZinc =
  newProject("repackagedZinc", file("target/tools/zinc"))
  .settings(
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),
    libraryDependencies += Dependencies.zinc,
    ideSkipProject := true
  )

updateIdea := {
  val baseDir = ideaBaseDirectory.value
  val build = ideaBuild.in(ThisBuild).value

  try {
    updateIdeaTask(baseDir, IdeaEdition.Community, build, downloadSources = true, Seq.empty, streams.value)
  } catch {
    case e : sbt.TranslatedException if e.getCause.isInstanceOf[java.io.FileNotFoundException] =>
      val newBuild = build.split('.').init.mkString(".") + "-EAP-CANDIDATE-SNAPSHOT"
      streams.value.log.warn(s"Failed to download IDEA $build, trying $newBuild")
      IO.deleteIfEmpty(Set(baseDir))
      updateIdeaTask(baseDir, IdeaEdition.Community, newBuild, downloadSources = true, Seq.empty, streams.value)
  }
}

lazy val packILoopWrapper = taskKey[Unit]("Packs in repl-interface-sources.jar repl interface for worksheet repl mode")

packILoopWrapper := {
  val fn = iLoopWrapperPath.value

  IO.zip(Seq((fn, fn.getName)),
    baseDirectory.in(BuildRef(file(".").toURI)).value / "target" / "plugin" / "Scala" / "lib" / "jps" / "repl-interface-sources.jar")
}
