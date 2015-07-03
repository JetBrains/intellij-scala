import Keys.{`package` => pack}
import Common._

resolvers in ThisBuild ++=
  bintrayJetbrains.allResolvers :+
  Resolver.typesafeIvyRepo("releases")

lazy val sdkDirectory = SettingKey[File]("sdk-directory", "Path to SDK directory where unmanagedJars and IDEA are located")

sdkDirectory in ThisBuild := baseDirectory.in(ThisBuild).value / "SDK"

ideaBuild in ThisBuild := Versions.ideaVersion

ideaDownloadDirectory in ThisBuild := sdkDirectory.value / "ideaSDK"

lazy val scalaCommunity: Project =
  newProject("scalaCommunity", "")
  .dependsOn(compilerSettings, runners % "test->test;compile->compile")
  .aggregate(jpsPlugin, sbtRuntimeDependencies, testDownloader, compilerSettings, runners, nailgunRunners, scalaRunner)
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects"),
    javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions in Global += "-target:jvm-1.6",
    libraryDependencies ++= DependencyGroups.scalaCommunity,
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
    unmanagedJars in Compile ++= unmanagedJarsFrom(sdkDirectory.value, "scalap", "nailgun", "scalastyle", "scalatest-finders"),
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
    ideaInternalPluginsJars <<= (ideaInternalPluginsJars).map { classpath =>
      classpath.filterNot(_.data.getName.contains("lucene-core"))
    },
    aggregate.in(updateIdea) := false,
    // jar hell workaround(ignore idea bundled lucene in test runtime)
    fullClasspath in Test := {(fullClasspath in Test).value.filterNot(_.data.getName.endsWith("lucene-core-2.4.1.jar"))},
    baseDirectory in Test := baseDirectory.value.getParentFile,
    fork in Test := true,
    parallelExecution := false,
    javaOptions in Test := Seq(
      //  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
      "-Xms128m",
      "-Xmx1024m",
      "-XX:MaxPermSize=350m",
      "-ea",
      s"-Didea.system.path=${Path.userHome}/.IdeaData/IDEA-15/scala/test-system",
      s"-Didea.config.path=${Path.userHome}/.IdeaData/IDEA-15/scala/test-config",
      s"-Dplugin.path=${baseDirectory.value}/out/plugin/Scala"
    )
  )

lazy val jpsPlugin  =
  newProject("jpsPlugin", "jps-plugin")
  .dependsOn(compilerSettings)
  .enablePlugins(SbtIdeaPlugin)
  .settings(unmanagedJars in Compile ++= unmanagedJarsFrom(sdkDirectory.value, "sbt", "nailgun"))

lazy val compilerSettings =
  newProject("compilerSettings", "compiler-settings")
  .enablePlugins(SbtIdeaPlugin)
  .settings(unmanagedJars in Compile ++= unmanagedJarsFrom(sdkDirectory.value, "nailgun"))

lazy val scalaRunner =
  newProject("scalaRunner", "ScalaRunner")
  .settings(libraryDependencies ++= DependencyGroups.scalaRunner)

lazy val runners =
  newProject("runners", "Runners")
  .dependsOn(scalaRunner)
  .settings(libraryDependencies ++= DependencyGroups.runners)

lazy val nailgunRunners =
  newProject("nailgunRunners", "NailgunRunners")
  .dependsOn(scalaRunner)
  .settings(unmanagedJars in Compile ++= unmanagedJarsFrom(sdkDirectory.value, "nailgun"))

lazy val ideaRunner =
  newProject("ideaRunner", "idea-runner")
  .dependsOn(Seq(compilerSettings, scalaRunner, runners, scalaCommunity, jpsPlugin, nailgunRunners).map(_ % Provided): _*)
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
  .settings(libraryDependencies ++= DependencyGroups.sbtRuntime)

lazy val testDownloader =
  newProject("testJarsDownloader")
  .settings(
    conflictWarning  := ConflictWarning.disable,
    resolvers ++= Seq(
      "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
    ),
    libraryDependencies ++= DependencyGroups.testDownloader,
    dependencyOverrides ++= Set(
      "org.scalatest" % "scalatest_2.10" % "2.1.7",
      "org.scalatest" % "scalatest_2.11" % "2.1.7",
      "org.scalatest" % "scalatest_2.10" % "1.9.2",
      "com.chuusai" % "shapeless_2.11" % "2.0.0"
    )
  )

lazy val pluginPackager =
  newProject("pluginPackager")
  .settings(
    artifactPath := baseDirectory.in(ThisBuild).value / "out" / "plugin" / "Scala",
    dependencyClasspath <<= (
      dependencyClasspath in (scalaCommunity, Compile),
      dependencyClasspath in (runners, Compile),
      dependencyClasspath in (sbtRuntimeDependencies, Compile)
    ).map { (a,b,c) => a ++ b ++ c },
    fileMappings := {
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
        Artifact(pack.in(compilerSettings, Compile).value,
          "lib/compiler-settings.jar"),
        Artifact(pack.in(nailgunRunners, Compile).value,
          "lib/scala-nailgun-runner.jar"),
        MergedArtifact(Seq(
            pack.in(runners, Compile).value,
            pack.in(scalaRunner, Compile).value),
          "lib/scala-plugin-runners.jar"),
        Library(Dependencies.scalaLibrary,
          "lib/scala-library.jar"),
        Directory(sdkDirectory.value / "scalap",
          "lib"),
        Directory(sdkDirectory.value / "scalastyle",
          "lib")
      ) ++
        crossLibraries.map { lib =>
          Library(lib.copy(name = lib.name + "_2.11"), s"${lib.name}.jar")
        } ++
        librariesToCopyAsIs.map { lib =>
          Library(lib, s"${lib.name}.jar")
        }
      Packaging.convertEntriesToFileMappings(jps ++ lib ++ launcher, artifactPath.value, dependencyClasspath.value)
    },
    pack := {
      Packaging.packagePlugin(fileMappings.value, artifactPath.value)
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


onLoad in Global := ((s: State) => { "updateIdea" :: s}) compose (onLoad in Global).value

addCommandAlias("downloadIdea", "updateIdea")

addCommandAlias("packagePlugin", "pluginPackager/package")

addCommandAlias("packagePluginZip", "pluginCompressor/package")
