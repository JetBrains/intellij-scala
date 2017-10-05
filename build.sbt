import java.io.File

import Common._
import com.dancingrobot84.sbtidea.tasks.{UpdateIdea => updateIdeaTask}
import sbt.Keys.{`package` => pack}

// Global build settings

resolvers in ThisBuild ++=
  BintrayJetbrains.allResolvers :+
  Resolver.typesafeIvyRepo("releases")

resolvers in ThisBuild += Resolver.sonatypeRepo("snapshots")

lazy val sdkDirectory = SettingKey[File]("sdk-directory", "Path to SDK directory where unmanagedJars and IDEA are located")

sdkDirectory in ThisBuild := baseDirectory.in(ThisBuild).value / "SDK"

ideaBuild in ThisBuild := Versions.ideaVersion

ideaDownloadDirectory in ThisBuild := sdkDirectory.value / "ideaSDK"

onLoad in Global := ((s: State) => { "updateIdea" :: s}) compose (onLoad in Global).value

addCommandAlias("downloadIdea", "updateIdea")

addCommandAlias("packagePluginCommunity", "pluginPackagerCommunity/package")

addCommandAlias("packagePluginCommunityZip", "pluginCompressorCommunity/package")

// Main projects
lazy val scalaCommunityRoot: sbt.Project =
  newProject("scalaCommunityRoot", file("."))
    .aggregate(scalaCommunity)

lazy val scalaCommunity: sbt.Project =
  newProject("scalaCommunity", file("scala/scala-impl"))
    .dependsOn(jpsShared, decompiler % "test->test;compile->compile", runners % "test->test;compile->compile", macroAnnotations, hocon)
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
    ideaInternalPlugins ++= Seq(
      "copyright",
      "gradle",
      "Groovy",
      "IntelliLang",
      "java-i18n",
      "android",
      "maven",
      "junit"
    ),
    ideaInternalPluginsJars :=
      ideaInternalPluginsJars.value
        .filterNot(cp => cp.data.getName.contains("lucene-core") || cp.data.getName.contains("junit-jupiter-api"))
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
    )
  )

lazy val jpsPlugin =
  newProject("jpsPlugin", file("jps-plugin"))
  .dependsOn(jpsShared)
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    libraryDependencies ++=
      Seq(Dependencies.nailgun) ++
        DependencyGroups.sbtBundled
  )

lazy val jpsShared =
  newProject("jpsShared", file("jpsShared"))
  .enablePlugins(SbtIdeaPlugin)
  .settings(libraryDependencies += Dependencies.nailgun)

lazy val scalaRunner =
  newProject("scalaRunner", file("ScalaRunner"))
  .settings(
    libraryDependencies ++= DependencyGroups.scalaRunner,
    // WORKAROUND fixes build error in sbt 0.13.12+ analogously to https://github.com/scala/scala/pull/5386/
    ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))
  )

lazy val runners =
  newProject("runners", file("Runners"))
  .dependsOn(scalaRunner)
  .settings(
    libraryDependencies ++= DependencyGroups.runners,
    // WORKAROUND fixes build error in sbt 0.13.12+ analogously to https://github.com/scala/scala/pull/5386/
    ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))
  )

lazy val nailgunRunners =
  newProject("nailgunRunners", file("NailgunRunners"))
  .dependsOn(scalaRunner)
  .settings(libraryDependencies += Dependencies.nailgun)

lazy val decompiler =
  newProject("decompiler", file("decompiler"))
    .settings(commonTestSettings(packagedPluginDir):_*)
    .settings(libraryDependencies ++= DependencyGroups.decompiler)

lazy val macroAnnotations =
  newProject("macroAnnotations", file("macroAnnotations"))
  .settings(Seq(
    addCompilerPlugin(Dependencies.macroParadise),
    libraryDependencies ++= Seq(Dependencies.scalaReflect, Dependencies.scalaCompiler)
  ): _*)

lazy val hocon =
  newProject("hocon")
    .enablePlugins(SbtIdeaPlugin)
    .settings(ideaInternalPlugins += "properties")

// Utility projects

lazy val ideaRunner =
  newProject("ideaRunner", file("idea-runner"))
  .dependsOn(Seq(jpsShared, scalaRunner, runners, scalaCommunity, jpsPlugin, nailgunRunners, decompiler).map(_ % Provided): _*)
  .settings(
    autoScalaLibrary := false,
    unmanagedJars in Compile := ideaMainJars.in(scalaCommunity).value,
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
      s"-Didea.system.path=${System.getProperty("user.home")}/.IdeaData/IDEA-14/scala/system",
      s"-Didea.config.path=${System.getProperty("user.home")}/.IdeaData/IDEA-14/scala/config",
      "-Dapple.laf.useScreenMenuBar=true",
      s"-Dplugin.path=${baseDirectory.value.getParentFile}/out/plugin/Scala",
      "-Didea.ProcessCanceledException=disabled"
    ),
    products in Compile := {
      (products in Compile).value :+ (pack in pluginPackagerCommunity).value
    }
  )

lazy val sbtRuntimeDependencies = project
  .settings(
    libraryDependencies := DependencyGroups.sbtRuntime,
    managedScalaInstance := false,
    conflictManager := ConflictManager.all,
    conflictWarning := ConflictWarning.disable,
    resolvers += sbt.Classpaths.sbtPluginReleases
  )

lazy val testDownloader =
  newProject("testJarsDownloader")
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
  newProject("sbtLaunchTestDownloader")
  .settings(
    autoScalaLibrary := false,
    conflictManager := ConflictManager.all,
    libraryDependencies ++= DependencyGroups.sbtLaunchTestDownloader,
    ideSkipProject := true
  )

lazy val jmhBenchmarks =
  newProject("jmhBenchmarks")
    .dependsOn(scalaCommunity % "test->test")
    .enablePlugins(JmhPlugin)

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
  IO.delete(testSystemDir)
  IO.delete(testConfigDir)
}

concurrentRestrictions in Global := Seq(
  Tags.limit(Tags.Test, 1)
)

// Packaging projects

lazy val packagedPluginDir = settingKey[File]("Path to packaged, but not yet compressed plugin")

packagedPluginDir in ThisBuild := baseDirectory.in(ThisBuild).value / "out" / "plugin" / "Scala"

lazy val iLoopWrapperPath = settingKey[File]("Path to repl interface sources")

iLoopWrapperPath := baseDirectory.in(jpsPlugin).value / "resources" / "ILoopWrapperImpl.scala"


lazy val pluginPackagerCommunity =
  newProject("pluginPackagerCommunity")
  .settings(
    artifactPath := packagedPluginDir.value,
    dependencyClasspath :=
      dependencyClasspath.in(scalaCommunity, Compile).value ++
      dependencyClasspath.in(jpsPlugin, Compile).value ++
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
        Artifact(pack.in(jpsPlugin, Compile).value,
          "lib/jps/scala-jps-plugin.jar"),
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
        Artifact(Packaging.putInTempJar(baseDirectory.in(jpsPlugin).value / "resources" / "ILoopWrapperImpl.scala" ),
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
        Artifact(pack.in(scalaCommunity, Compile).value,
          "lib/scala-plugin.jar"),
        Artifact(pack.in(decompiler, Compile).value,
          "lib/scalap.jar"),
        Artifact(pack.in(jpsShared, Compile).value,
          "lib/jpsShared.jar"),
        Artifact(pack.in(nailgunRunners, Compile).value,
          "lib/scala-nailgun-runner.jar"),
        MergedArtifact(Seq(
            pack.in(runners, Compile).value,
            pack.in(scalaRunner, Compile).value),
          "lib/scala-plugin-runners.jar"),
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
    }
  )


lazy val pluginCompressorCommunity =
  newProject("pluginCompressorCommunity")
  .settings(
    artifactPath := baseDirectory.in(ThisBuild).value / "out" / "scala-plugin.zip",
    pack := {
      Packaging.compressPackagedPlugin(pack.in(pluginPackagerCommunity).value, artifactPath.value)
      artifactPath.value
    }
  )

lazy val repackagedZinc =
  newProject("repackagedZinc")
  .settings(
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),
    libraryDependencies += Dependencies.zinc
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
    baseDirectory.in(BuildRef(file(".").toURI)).value / "out" / "plugin" / "Scala" / "lib" / "jps" / "repl-interface-sources.jar")
}
