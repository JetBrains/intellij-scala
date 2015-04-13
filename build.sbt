import Keys.{`package` => pack}

resolvers in ThisBuild ++= bintrayJetbrains.allResolvers

resolvers in ThisBuild += Resolver.typesafeIvyRepo("releases")

lazy val sbtRuntimeDependencies = newProject("sbtRuntimeDependencies")(
  libraryDependencies ++= DependencyGroups.sbtRuntime
)

lazy val testDownloader = newProject("testJarsDownloader")()

lazy val scalaCommunity = newProject("scalaCommunity", "")(
    libraryDependencies ++= DependencyGroups.scalaCommunity,
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"
  )
    .dependsOn(compilerSettings, runners % "test->test;compile->compile")
  .aggregate(jpsPlugin, sbtRuntimeDependencies)

update := {
  (update in testDownloader).value
  update.value
}

unmanagedSourceDirectories in Compile += baseDirectory.value /  "src"

unmanagedSourceDirectories in Test += baseDirectory.value /  "test"

unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"

ideExcludedDirectories := Seq(baseDirectory.value / "testdata" / "projects")

javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions in Global += "-target:jvm-1.6"

ideaVersion := "142.2670.3"

ideaBasePath in Global := baseDirectory.value / "SDK" / "ideaSDK" / s"idea-${ideaVersion.value}"

ideaBaseJars in Global := (ideaBasePath.value  / "lib" * "*.jar").classpath

ideaICPluginJars in Global := {
  val basePluginsDir = ideaBasePath.value  / "plugins"
  val baseDirectories =
    basePluginsDir / "copyright" / "lib" +++
      basePluginsDir / "gradle" / "lib" +++
      basePluginsDir / "Groovy" / "lib" +++
      basePluginsDir / "IntelliLang" / "lib" +++
      basePluginsDir / "java-i18n" / "lib" +++
      basePluginsDir / "android" / "lib" +++
      basePluginsDir / "maven" / "lib" +++
      basePluginsDir / "junit" / "lib" +++
      basePluginsDir / "properties" / "lib"
  val customJars = baseDirectories * (globFilter("*.jar") -- "*asm*.jar" -- "*lucene-core*")
  customJars.classpath
}

allIdeaJars in Global := (ideaBaseJars in Global).value ++ (ideaICPluginJars in Global).value

unmanagedJars in Compile := allIdeaJars.value

unmanagedJars in Compile ++= (baseDirectory.value /  "SDK/scalap" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value /  "SDK/nailgun" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value /  "SDK/scalastyle" * "*.jar").classpath

unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

unmanagedJars in Compile ++= (baseDirectory.value /  "SDK/scalatest-finders" * "*.jar").classpath

lazy val compilerSettings = newProject("compilerSettings", "compiler-settings")(
    unmanagedJars in Compile := allIdeaJars.value
  )

lazy val scalaRunner = newProject("scalaRunner", "ScalaRunner")()

lazy val runners = newProject("runners", "Runners")().dependsOn(scalaRunner)

lazy val jpsPlugin = newProject("jpsPlugin", "jps-plugin")(
    unmanagedJars in Compile := allIdeaJars.value
  )
  .dependsOn(compilerSettings)

lazy val ideaRunner = newProject("ideaRunner", "idea-runner")(
    unmanagedJars in Compile := (ideaBasePath.value  / "lib" * "*.jar").classpath, autoScalaLibrary := false
  )
  .dependsOn(Seq(compilerSettings, scalaRunner, runners, scalaCommunity, jpsPlugin, nailgunRunners).map(_ % Provided): _*)

lazy val nailgunRunners = newProject("nailgunRunners", "NailgunRunners")().dependsOn(scalaRunner)

ideaResolver := {
  val ideaVer = ideaVersion.value
  val ideaSDKPath = ideaBasePath.value.getParentFile
  val ideaArchiveName = ideaSDKPath.getAbsolutePath + s"/ideaSDK${ideaVersion.value}.arc"
  def renameFun = (ideaSDKPath.listFiles sortWith { _.lastModified > _.lastModified }).head.renameTo(ideaBasePath.value)
  val s = ideaVer.substring(0, ideaVer.indexOf('.'))
  IdeaResolver(
    teamcityURL = "https://teamcity.jetbrains.com",
    buildTypes = Seq("bt410"),
    branch = s"idea/${ideaVersion.value}",
    artifacts = Seq(
      System.getProperty("os.name") match {
        case r"^Linux"     => (s"/ideaIC-$s.SNAPSHOT.tar.gz", ideaArchiveName,  Some({ _: File => s"tar xvfz $ideaArchiveName -C ${ideaSDKPath.getAbsolutePath}".!; renameFun}))
        case r"^Mac OS.*"  => (s"/ideaIC-$s.SNAPSHOT.win.zip", ideaArchiveName, Some({ _: File => s"unzip $ideaArchiveName -d ${ideaBasePath.value}".!; renameFun}))
        case r"^Windows.*" => (s"/ideaIC-$s.SNAPSHOT.win.zip", ideaArchiveName, Some({ _: File => IO.unzip(file(ideaArchiveName), ideaBasePath.value); renameFun}))
        case other => throw new IllegalStateException(s"OS $other is not supported")
      },
      ("/sources.zip",  ideaBasePath.value.getAbsolutePath + "/sources.zip")
    )
  )
}

downloadIdea := {
  val log = streams.value.log
  val ideaSDKPath = ideaBasePath.value.getParentFile
  val resolver = (ideaResolver in Compile).value
  val buildId = getBuildId(resolver).getOrElse("")
  val artifactBaseUrl = resolver.teamcityURL + s"/guestAuth/app/rest/builds/id:$buildId/artifacts/content"
  if (!ideaSDKPath.exists) ideaSDKPath.mkdirs
  def downloadDep(art: TCArtifact): Unit = {
    val fileTo = file(art.to)
    if (!fileTo.exists() || art.overwrite) {
      log.info(s"downloading${if (art.overwrite) "(overwriting)" else ""}: ${art.from} -> ${fileTo.getAbsolutePath}")
      IO.download(url(artifactBaseUrl + art.from), fileTo)
      log.success(s"download of ${fileTo.getName} finished")
      art.extractFun foreach { func =>
        log.info(s"extracting from archive ${fileTo.getName}")
        func(fileTo)
        log.success("extract finished")
      }
    } else log.info(s"$fileTo already exists, skipping")
  }
  resolver.artifacts foreach downloadDep
}

// tests

fork in Test := true

parallelExecution := false

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

// jar hell workaround(ignore idea bundled lucene in test runtime)
fullClasspath in Test := {(fullClasspath in Test).value.filterNot(_.data.getName.endsWith("lucene-core-2.4.1.jar"))}

baseDirectory in Test := baseDirectory.value.getParentFile

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
