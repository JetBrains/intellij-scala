import Common._
import com.dancingrobot84.sbtidea.Tasks.{updateIdea => updateIdeaTask}
import sbt.Keys.{`package` => pack}

// Global build settings

resolvers in ThisBuild ++=
  BintrayJetbrains.allResolvers :+
  Resolver.typesafeIvyRepo("releases") :+
    Resolver.sonatypeRepo("snapshots")

lazy val sdkDirectory = SettingKey[File]("sdk-directory", "Path to SDK directory where unmanagedJars and IDEA are located")

sdkDirectory in ThisBuild := baseDirectory.in(ThisBuild).value / "SDK"

ideaBuild in ThisBuild := Versions.ideaVersion

ideaDownloadDirectory in ThisBuild := sdkDirectory.value / "ideaSDK"

onLoad in Global := ((s: State) => { "updateIdea" :: s}) compose (onLoad in Global).value

addCommandAlias("downloadIdea", "updateIdea")

addCommandAlias("packagePlugin", "pluginPackager/package")

addCommandAlias("packagePluginZip", "pluginCompressor/package")

// Main projects
lazy val scalaCommunity: Project =
  newProject("scalaCommunity", file("."))
  .dependsOn(compilerSettings, scalap, runners % "test->test;compile->compile", macroAnnotations)
  .enablePlugins(SbtIdeaPlugin)
  .settings(commonTestSettings(packagedPluginDir):_*)
  .settings(
    ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects"),
    javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions in Global += "-target:jvm-1.6",
    //scalacOptions in Global += "-Xmacro-settings:analyze-caches",
    libraryDependencies ++= DependencyGroups.scalaCommunity,
    unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
    unmanagedJars in Compile ++= unmanagedJarsFrom(sdkDirectory.value, "nailgun"),
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
    ideaInternalPluginsJars <<= ideaInternalPluginsJars.map { classpath =>
      classpath.filterNot(_.data.getName.contains("lucene-core"))
    },
    aggregate.in(updateIdea) := false,
    test in Test <<= test.in(Test).dependsOn(setUpTestEnvironment),
    testOnly in Test <<= testOnly.in(Test).dependsOn(setUpTestEnvironment)
  )

lazy val jpsPlugin  =
  newProject("jpsPlugin", file("jps-plugin"))
  .dependsOn(compilerSettings)
  .enablePlugins(SbtIdeaPlugin)
  .settings(unmanagedJars in Compile ++= unmanagedJarsFrom(sdkDirectory.value, "sbt", "nailgun"))

lazy val compilerSettings =
  newProject("compilerSettings", file("compiler-settings"))
  .enablePlugins(SbtIdeaPlugin)
  .settings(unmanagedJars in Compile ++= unmanagedJarsFrom(sdkDirectory.value, "nailgun"))

lazy val scalaRunner =
  newProject("scalaRunner", file("ScalaRunner"))
  .settings(libraryDependencies ++= DependencyGroups.scalaRunner)

lazy val runners =
  newProject("runners", file("Runners"))
  .dependsOn(scalaRunner)
  .settings(libraryDependencies ++= DependencyGroups.runners)

lazy val nailgunRunners =
  newProject("nailgunRunners", file("NailgunRunners"))
  .dependsOn(scalaRunner)
  .settings(unmanagedJars in Compile ++= unmanagedJarsFrom(sdkDirectory.value, "nailgun"))

lazy val scalap =
  newProject("scalap", file("scalap"))
    .settings(commonTestSettings(packagedPluginDir):_*)
    .settings(libraryDependencies ++= DependencyGroups.scalap)

lazy val scalaDevPlugin =
  newProject("scalaDevPlugin", file("SDK/scalaDevPlugin"))
  .settings(unmanagedJars in Compile := ideaMainJars.in(scalaCommunity).value)

lazy val macroAnnotations =
  newProject("macroAnnotations", file("macroAnnotations"))
  .settings(Seq(
    addCompilerPlugin(Dependencies.macroParadise),
    libraryDependencies ++= Seq(Dependencies.scalaReflect, Dependencies.scalaCompiler)
  ): _*)

// Utility projects

lazy val ideaRunner =
  newProject("ideaRunner", file("idea-runner"))
  .dependsOn(Seq(compilerSettings, scalaRunner, runners, scalaCommunity, jpsPlugin, nailgunRunners, scalap).map(_ % Provided): _*)
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
      "-Didea.system.path=/home/miha/.IdeaData/IDEA-14/scala/system",
      "-Didea.config.path=/home/miha/.IdeaData/IDEA-14/scala/config",
      "-Dapple.laf.useScreenMenuBar=true",
      s"-Dplugin.path=${baseDirectory.value.getParentFile}/out/plugin",
      "-Didea.ProcessCanceledException=disabled"
    )
  )

lazy val sbtRuntimeDependencies =
  newProject("sbtRuntimeDependencies")
  .settings(
    libraryDependencies ++= DependencyGroups.sbtRuntime,
    autoScalaLibrary := false
  )

lazy val testDownloader =
  newProject("testJarsDownloader")
  .settings(
    conflictManager := ConflictManager.all,
    conflictWarning  := ConflictWarning.disable,
    resolvers ++= Seq(
      "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
    ),
    libraryDependencies ++= DependencyGroups.testDownloader,
    dependencyOverrides ++= Set(
      "com.chuusai" % "shapeless_2.11" % "2.0.0"
    ),
    update <<= update.dependsOn(update.in(sbtLaunchTestDownloader))
  )

lazy val sbtLaunchTestDownloader =
  newProject("sbtLaunchTestDownloader")
  .settings(
    autoScalaLibrary := false,
    conflictManager := ConflictManager.all,
    libraryDependencies ++= DependencyGroups.sbtLaunchTestDownloader
  )

// Testing keys and settings

addCommandAlias("runSlowTests", s"testOnly -- --include-categories=$slowTestsCategory")

addCommandAlias("runFastTests", s"testOnly -- --exclude-categories=$slowTestsCategory")

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

lazy val pluginPackager =
  newProject("pluginPackager")
  .settings(
    artifactPath := packagedPluginDir.value,
    dependencyClasspath <<= (
      dependencyClasspath in (scalaCommunity, Compile),
      dependencyClasspath in (runners, Compile),
      dependencyClasspath in (sbtRuntimeDependencies, Compile)
    ).map { (a,b,c) => a ++ b ++ c },
    mappings := {
      import Packaging.PackageEntry._
      val crossLibraries = List(Dependencies.scalaParserCombinators, Dependencies.scalaXml)
      val librariesToCopyAsIs = DependencyGroups.scalaCommunity.filterNot { lib =>
        crossLibraries.contains(lib) || lib == Dependencies.scalaLibrary
      }
      val jps = Seq(
        Artifact(pack.in(jpsPlugin, Compile).value,
          "lib/jps/scala-jps-plugin.jar"),
        Directory(sdkDirectory.value / "nailgun",
          "lib/jps"),
        Directory(sdkDirectory.value / "sbt",
          "lib/jps")
      )
      val launcher = Seq(
        Library(Dependencies.sbtStructureExtractor012,
          "launcher/sbt-structure-0.12.jar"),
        Library(Dependencies.sbtStructureExtractor013,
          "launcher/sbt-structure-0.13.jar"),
        Library(Dependencies.sbtLaunch,
          "launcher/sbt-launch.jar")
      )
      val lib = Seq(
        Artifact(pack.in(scalaCommunity, Compile).value,
          "lib/scala-plugin.jar"),
        Artifact(pack.in(scalap, Compile).value,
          "lib/scalap.jar"),
        Artifact(pack.in(compilerSettings, Compile).value,
          "lib/compiler-settings.jar"),
        Artifact(pack.in(nailgunRunners, Compile).value,
          "lib/scala-nailgun-runner.jar"),
        MergedArtifact(Seq(
            pack.in(runners, Compile).value,
            pack.in(scalaRunner, Compile).value),
          "lib/scala-plugin-runners.jar"),
        Library(Dependencies.scalaLibrary,
          "lib/scala-library.jar")
      ) ++
        crossLibraries.map { lib =>
          Library(lib.copy(name = lib.name + "_2.11"), s"lib/${lib.name}.jar")
        } ++
        librariesToCopyAsIs.map { lib =>
          Library(lib, s"lib/${lib.name}.jar")
        }
      Packaging.convertEntriesToMappings(jps ++ lib ++ launcher, dependencyClasspath.value)
    },
    pack := {
      Packaging.packagePlugin(mappings.value, artifactPath.value)
      artifactPath.value
    }
  )

lazy val pluginCompressor =
  newProject("pluginCompressor")
  .settings(
    artifactPath := baseDirectory.in(ThisBuild).value / "out" / "scala-plugin.zip",
    pack := {
      Packaging.compressPackagedPlugin(pack.in(pluginPackager).value, artifactPath.value)
      artifactPath.value
    }
  )

TaskKey[Unit]("buildDevPlugin") := {
  (packageBin in (scalaDevPlugin, Compile)).value
}

updateIdea <<= (ideaBaseDirectory, ideaBuild.in(ThisBuild), streams).map {
  (baseDir, build, streams) =>
    try {
      updateIdeaTask(baseDir, build, Seq.empty, streams)
    } catch {
      case e : sbt.TranslatedException if e.getCause.isInstanceOf[java.io.FileNotFoundException] =>
        val newBuild = build.split('.').init.mkString(".") + "-EAP-CANDIDATE-SNAPSHOT"
        streams.log.warn(s"Failed to download IDEA $build, trying $newBuild")
        IO.deleteIfEmpty(Set(baseDir))
        updateIdeaTask(baseDir, newBuild, Seq.empty, streams)
    }
  }
