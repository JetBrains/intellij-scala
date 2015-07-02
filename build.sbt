import Keys.{`package` => pack}
import Common._
import CustomKeys._
import Packaging._

resolvers in ThisBuild ++=
  bintrayJetbrains.allResolvers :+
  Resolver.typesafeIvyRepo("releases")

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
    unmanagedJars in Compile ++= unmanagedJarsFromSdk("scalap", "nailgun", "scalastyle", "scalatest-finders").value,
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
  .settings(packageSettings:_*)
  .settings(
    dependencyClasspath.in(packagePlugin) <<= (
      dependencyClasspath in Compile,
      dependencyClasspath in (runners, Compile),
      dependencyClasspath in (sbtRuntimeDependencies, Compile)
    ).map { (a,b,c) => a ++ b ++ c },
    packagePlugin <<= packagePlugin.dependsOn(
      pack in Compile,
      pack in (compilerSettings, Compile),
      pack in (jpsPlugin, Compile),
      pack in (nailgunRunners, Compile),
      pack in (runners, Compile),
      pack in (scalaRunner, Compile)
    )
  )

lazy val jpsPlugin  =
  newProject("jpsPlugin", "jps-plugin")
  .dependsOn(compilerSettings)
  .enablePlugins(SbtIdeaPlugin)
  .settings(unmanagedJars in Compile ++= unmanagedJarsFromSdk("sbt", "nailgun").value)

lazy val compilerSettings =
  newProject("compilerSettings", "compiler-settings")
  .enablePlugins(SbtIdeaPlugin)
  .settings(unmanagedJars in Compile ++= unmanagedJarsFromSdk("nailgun").value)

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
  .settings(unmanagedJars in Compile ++= unmanagedJarsFromSdk("nailgun").value)

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

packageStructure := {
  import PackageEntry._
  val crossLibraries = List(Dependencies.scalaParserCombinators, Dependencies.scalaXml)
  val librariesToCopyAsIs = DependencyGroups.scalaCommunity.filterNot { lib =>
    crossLibraries.contains(lib) || lib == Dependencies.scalaLibrary
  }
  val jps = Seq(
    Artifact(artifactPath.in(jpsPlugin, Compile, packageBin).value,
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
    Artifact(artifactPath.in(scalaCommunity, Compile, packageBin).value,
      "lib/scala-plugin.jar"),
    Artifact(artifactPath.in(compilerSettings, Compile, packageBin).value,
      "lib/compiler-settings.jar"),
    Artifact(artifactPath.in(nailgunRunners, Compile, packageBin).value,
      "lib/scala-nailgun-runner.jar"),
    MergedArtifact(Seq(
        artifactPath.in(runners, Compile, packageBin).value,
        artifactPath.in(scalaRunner, Compile, packageBin).value),
      "lib/scala-plugin-runners.jar"),
    Library(Dependencies.scalaLibrary,
      "lib/scala-library.jar"),
    Directory(sdkDirectory.value / "scalap",
      "lib"),
    Directory(sdkDirectory.value / "scalastyle",
      "lib")
  ) ++
    crossLibraries.map { lib =>
      new Library(lib.copy(name = lib.name + "_2.11"), s"${lib.name}.jar")
    } ++
    librariesToCopyAsIs.map { lib =>
      new Library(lib, s"${lib.name}.jar")
    }
  jps ++ lib ++ launcher
}

onLoad in Global := ((s: State) => { "updateIdea" :: s}) compose (onLoad in Global).value

addCommandAlias("downloadIdea", "updateIdea")
