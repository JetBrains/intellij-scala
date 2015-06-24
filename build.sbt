import Keys.{`package` => pack}
import Common._
import Packaging._

resolvers in ThisBuild ++= bintrayJetbrains.allResolvers

resolvers in ThisBuild += Resolver.typesafeIvyRepo("releases")

lazy val commonIdeaSettings = ideaPluginSettings ++ Seq(
  ideaBuild := Versions.ideaVersion,
  ideaDownloadDirectory := baseDirectory.in(ThisBuild).value / "SDK" / "ideaSDK"
)

lazy val scalaCommunity =
  newProject("scalaCommunity", "")(
    ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects"),
    javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions in Global += "-target:jvm-1.6",
    libraryDependencies ++= DependencyGroups.scalaCommunity,
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
    unmanagedJars in Compile ++= unmanagedJarsFrom("scalap", "nailgun", "scalastyle", "scalatest-finders").value,
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
  .settings(commonIdeaSettings:_*)
  .settings(
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
    aggregate.in(updateIdea) := false
  )
  .dependsOn(compilerSettings, runners % "test->test;compile->compile")
  .aggregate(jpsPlugin, sbtRuntimeDependencies, testDownloader)

lazy val jpsPlugin  =
  newProject("jpsPlugin", "jps-plugin")(
    unmanagedJars in Compile ++= unmanagedJarsFrom("sbt", "nailgun").value
  )
  .settings(commonIdeaSettings:_*)
  .dependsOn(compilerSettings)

lazy val compilerSettings =
  newProject("compilerSettings", "compiler-settings")(
    unmanagedJars in Compile ++= unmanagedJarsFrom("nailgun").value
  )
  .settings(commonIdeaSettings:_*)

lazy val scalaRunner =
  newProject("scalaRunner", "ScalaRunner")(
    libraryDependencies ++= DependencyGroups.scalaRunner
  )

lazy val runners =
  newProject("runners", "Runners")(
    libraryDependencies ++= DependencyGroups.runners
  ).dependsOn(scalaRunner)

lazy val nailgunRunners =
  newProject("nailgunRunners", "NailgunRunners")(
    unmanagedJars in Compile ++= unmanagedJarsFrom("nailgun").value
  )
  .dependsOn(scalaRunner)

lazy val ideaRunner =
  newProject("ideaRunner", "idea-runner")(
    autoScalaLibrary := false,
    unmanagedJars in Compile := ideaMainJars.in(scalaCommunity).value,
    unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
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
  .dependsOn(Seq(compilerSettings, scalaRunner, runners, scalaCommunity, jpsPlugin, nailgunRunners).map(_ % Provided): _*)

lazy val sbtRuntimeDependencies =
  newProject("sbtRuntimeDependencies")(
    libraryDependencies ++= DependencyGroups.sbtRuntime
  )

lazy val testDownloader =
  newProject("testJarsDownloader")(
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

// packaging

pack in Compile <<= (pack in Compile) dependsOn (
  pack in (compilerSettings, Compile),
  pack in (jpsPlugin, Compile),
  pack in (runners, Compile),
  pack in (nailgunRunners, Compile),
  pack in (scalaRunner, Compile)
  )

lazy val packagePlugin = taskKey[Unit]("Package scala plugin locally")

lazy val packagePluginZip = taskKey[Unit]("Package and compress scala plugin locally")

packagePlugin := {
  val (dirs, files) = packageStructure.value.partition(_._1.isDirectory)
  val base = baseDirectory.in(ThisBuild).value / "out" / "plugin" / "Scala"
  IO.delete(base.getParentFile)
  dirs  foreach { case (from, to) => IO.copyDirectory(from, base / to, overwrite = true) }
  files foreach { case (from, to) => IO.copyFile(from, base / to)}
}

packagePlugin <<= packagePlugin.dependsOn(pack in Compile)

packagePluginZip <<= (packagePlugin, baseDirectory in ThisBuild).map { (_, baseDir) =>
  val base = baseDir / "out" / "plugin"
  val zipFile = baseDir / "out" / "scala-plugin.zip"
  IO.zip((base ***) pair (relativeTo(base), false), zipFile)
}
