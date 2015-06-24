import Keys.{`package` => pack}

resolvers in ThisBuild ++= bintrayJetbrains.allResolvers

resolvers in ThisBuild += Resolver.typesafeIvyRepo("releases")

lazy val commonIdeaSettings = ideaPluginSettings ++ Seq(
  ideaBuild := Versions.ideaVersion,
  ideaDownloadDirectory := baseDirectory.in(ThisBuild).value / "SDK" / "ideaSDK"
)

lazy val scalaCommunity =
  newProject("scalaCommunity", "")(
    unmanagedSourceDirectories in Compile += baseDirectory.value /  "src",
    unmanagedSourceDirectories in Test += baseDirectory.value /  "test",
    unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources",
    ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects"),
    javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6"),
    scalacOptions in Global += "-target:jvm-1.6",
    libraryDependencies ++= DependencyGroups.scalaCommunity,
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
    unmanagedJars in Compile ++= {
      val sdkDirs = Seq("scalap", "nailgun", "scalastyle", "scalatest-finders")
      val sdkPathFinder = sdkDirs.foldLeft(PathFinder.empty) { (finder, dir) =>
        finder +++ (baseDirectory.value / "SDK" / dir)
      }
      (sdkPathFinder * globFilter("*.jar")).classpath
    },
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
    unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/sbt" * "*.jar").classpath,
    unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath,
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
    unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"
  )
  .settings(commonIdeaSettings:_*)
  .dependsOn(compilerSettings)

lazy val compilerSettings =
  newProject("compilerSettings", "compiler-settings")(
    unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath,
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src"
  )
  .settings(commonIdeaSettings:_*)

lazy val scalaRunner =
  newProject("scalaRunner", "ScalaRunner")(
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    libraryDependencies += "org.specs2" %% "specs2" % "2.3.11" % "provided" excludeAll ExclusionRule(organization = "org.ow2.asm")
  )

lazy val runners =
  newProject("runners", "Runners")(
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2" % "2.3.11" % "provided"  excludeAll ExclusionRule(organization = "org.ow2.asm"),
      "org.scalatest" % "scalatest_2.11" % "2.2.1" % "provided",
      "com.lihaoyi" %% "utest" % "0.1.3" % "provided"
    )
  ).dependsOn(scalaRunner)

lazy val nailgunRunners =
  newProject("nailgunRunners", "NailgunRunners")(
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
    unmanagedJars in Compile ++= (baseDirectory.value.getParentFile / "SDK/nailgun" * "*.jar").classpath
  )
  .dependsOn(scalaRunner)

lazy val ideaRunner =
  newProject("ideaRunner", "idea-runner")(
    unmanagedJars in Compile := ideaMainJars.in(scalaCommunity).value,
    autoScalaLibrary := false,
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
    libraryDependencies ++= Seq(
      "org.scalatest" % "scalatest_2.11" % "2.2.1",
      "org.scalatest" % "scalatest_2.10" % "2.2.1",
      "org.specs2" % "specs2_2.11" % "2.4.15",
      "org.scalaz" % "scalaz-core_2.11" % "7.1.0",
      "org.scalaz" % "scalaz-concurrent_2.11" % "7.1.0",
      "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.2",
      "org.specs2" % "specs2_2.10" % "2.4.6",
      "org.scalaz" % "scalaz-core_2.10" % "7.1.0",
      "org.scalaz" % "scalaz-concurrent_2.10" % "7.1.0",
      "org.scalaz.stream" % "scalaz-stream_2.11" % "0.6a",
      "com.chuusai" % "shapeless_2.11" % "2.0.0",
      "org.typelevel" % "scodec-bits_2.11" % "1.1.0-SNAPSHOT",
      "org.typelevel" % "scodec-core_2.11" % "1.7.0-SNAPSHOT",
      "org.scalatest" % "scalatest_2.11" % "2.1.7",
      "org.scalatest" % "scalatest_2.10" % "2.1.7",
      "org.scalatest" % "scalatest_2.10" % "1.9.2",
      "com.github.julien-truffaut"  %%  "monocle-core"    % "1.2.0-SNAPSHOT",
      "com.github.julien-truffaut"  %%  "monocle-generic" % "1.2.0-SNAPSHOT",
      "com.github.julien-truffaut"  %%  "monocle-macro"   % "1.2.0-SNAPSHOT",
      "io.spray" %% "spray-routing" % "1.3.1"
    ),
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

packageStructure in Compile := {
  lazy val resolved = (
    (dependencyClasspath in Compile).value ++
    (dependencyClasspath in (runners, Compile)).value ++
    (dependencyClasspath in (scalaCommunity, Compile)).value ++
    (dependencyClasspath in (sbtRuntimeDependencies, Compile)).value
  ).map { f => f.metadata.get(moduleID.key) -> f.data}.toMap
   .collect { case (Some(x), y) => (x.organization % x.name % x.revision) -> y}
  def simplify(lib: ModuleID) = lib.organization % lib.name % lib.revision
  def libOf(lib: ModuleID, prefix: String = "lib/") = resolved(simplify(lib)) -> (prefix + resolved(simplify(lib)).name)
  val artifacts = Seq(
    (artifactPath in (scalaCommunity, Compile, packageBin)).value    -> "lib/scala-plugin.jar",
    (artifactPath in (compilerSettings, Compile, packageBin)).value -> "lib/compiler-settings.jar",
    (artifactPath in (nailgunRunners, Compile, packageBin)).value    -> "lib/scala-nailgun-runner.jar",
    (artifactPath in (jpsPlugin, Compile, packageBin)).value -> "lib/jps/scala-jps-plugin.jar",
    merge(
      (artifactPath in (runners, Compile, packageBin)).value,
      (artifactPath in (scalaRunner, Compile, packageBin)).value
    ) -> "lib/scala-plugin-runners.jar"
  )
  val unmanaged = Seq(
    file("SDK/nailgun") -> "lib/jps/",
    file("SDK/sbt") -> "lib/jps/",
    file("SDK/scalap") -> "lib/",
    file("SDK/scalastyle") -> "lib/"
  )
  val renamedLibraries = Seq(
    libOf(Dependencies.sbtStructureExtractor012)._1 -> "launcher/sbt-structure-0.12.jar",
    libOf(Dependencies.sbtStructureExtractor013)._1 -> "launcher/sbt-structure-0.13.jar",
    libOf(Dependencies.sbtLaunch)._1 -> "launcher/sbt-launch.jar",
  )
  val crossLibraries = Seq(Dependencies.scalaParserCombinators, Dependencies.scalaXml).map { lib =>
    libOf(lib.copy(name=lib.name + "_2.11"))
  }
  val processedLibraries = Seq(Dependencies.scalaLibrary, Dependencies.scalaParserCombinators, Dependencies.scalaXml)
  val libraries = DependencyGroups.scalaCommunity.filterNot(processedLibraries.contains).map(lib => libOf(lib))
  artifacts ++ unmanaged ++ renamedLibraries ++ crossLibraries ++ libraries
}

packagePlugin in Compile := {
  val (dirs, files) = (packageStructure in Compile).value.partition(_._1.isDirectory)
  val base = baseDirectory.value / "out" / "plugin" / "Scala"
  IO.delete(base.getParentFile)
  dirs  foreach { case (from, to) => IO.copyDirectory(from, base / to, overwrite = true) }
  files foreach { case (from, to) => IO.copyFile(from, base / to)}
}

packagePlugin in Compile <<= (packagePlugin in Compile) dependsOn (pack in Compile)

packagePluginZip in Compile := {
  val base = baseDirectory.value / "out" / "plugin"
  val zipFile = baseDirectory.value / "out" / "scala-plugin.zip"
  IO.zip((base ***) pair (relativeTo(base), false), zipFile)
}

packagePluginZip in Compile <<= (packagePluginZip in Compile) dependsOn (packagePlugin in Compile)
