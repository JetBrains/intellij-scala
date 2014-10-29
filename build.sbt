import Keys.{`package` => pack}

name :=  "ScalaCommunity"

organization :=  "JetBrains"

scalaVersion :=  "2.11.2"

libraryDependencies +=  "org.scalatest" % "scalatest-finders" % "0.9.6"

libraryDependencies +=  "org.atteo" % "evo-inflector" % "1.2"

libraryDependencies +=  "org.scala-lang.modules" %% "scala-xml" % "1.0.2"

libraryDependencies +=  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"

libraryDependencies +=  "org.scala-lang" % "scala-reflect" % scalaVersion.value

libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

unmanagedSourceDirectories in Compile += baseDirectory.value /  "src"

unmanagedSourceDirectories in Test += baseDirectory.value /  "test"

unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"

ideaVersion := "139.222.5"

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
  val customJars = baseDirectories * (globFilter("*.jar") -- "*asm*.jar")
  customJars.classpath
}

allIdeaJars in Global := (ideaBaseJars in Global).value ++ (ideaICPluginJars in Global).value

unmanagedJars in Compile := allIdeaJars.value

unmanagedJars in Compile ++= (baseDirectory.value /  "SDK/scalap" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value /  "SDK/nailgun" * "*.jar").classpath

unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

lazy val compiler_settings = project.in(file( "compiler-settings"))
  .settings(unmanagedJars in Compile := allIdeaJars.value)

lazy val ScalaRunner = project.in(file( "ScalaRunner"))

lazy val Runners = project.in(file( "Runners")).dependsOn(ScalaRunner)

lazy val ScalaCommunity = project.in(file("")).dependsOn(compiler_settings, Runners).aggregate(jps_plugin)

lazy val intellij_hocon = Project( "intellij-hocon", file("intellij-hocon")).dependsOn(ScalaCommunity)
  .settings(unmanagedJars in Compile := allIdeaJars.value)

lazy val intellij_scalastyle  =
  Project("intellij-scalastyle", file( "intellij-scalastyle")).dependsOn(ScalaCommunity)
    .settings(unmanagedJars in Compile := allIdeaJars.value)

lazy val jps_plugin = Project( "scala-jps-plugin", file("jps-plugin")).dependsOn(compiler_settings)
  .settings(unmanagedJars in Compile := allIdeaJars.value)

lazy val idea_runner = Project( "idea-runner", file("idea-runner"))
  .settings(unmanagedJars in Compile := (ideaBasePath.value  / "lib" * "*.jar").classpath)

lazy val NailgunRunners = project.in(file( "NailgunRunners")).dependsOn(ScalaRunner)

lazy val SBT = project.in(file( "SBT")).dependsOn(intellij_hocon, ScalaCommunity % "compile->compile;test->test")
  .settings(unmanagedJars in Compile := allIdeaJars.value)

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
        case r"^Linux"     => (s"/ideaIC-$s.SNAPSHOT.tar.gz", ideaArchiveName, Some({ _: File => s"tar xvfz $ideaArchiveName -C ${ideaSDKPath.getAbsolutePath}".!; renameFun}))
        case r"^Mac OS.*"  => (s"/ideaIC-$s.SNAPSHOT.win.zip", ideaArchiveName, Some({ _: File => s"unzip $ideaArchiveName -d ${ideaBasePath.value}".!; renameFun}))
        case r"^Windows.*" => (s"/ideaIC-$s.SNAPSHOT.win.zip", ideaArchiveName, Some({ _: File => IO.unzip(_: File, ideaBasePath.value); renameFun}))
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

parallelExecution := true

javaOptions in Test := Seq(
//  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
  "-Xms128m",
  "-Xmx1024m",
  "-XX:MaxPermSize=350m",
  "-ea",
  s"-Didea.system.path=${Path.userHome}/.IdeaData/IDEA-14/scala/test-system",
  s"-Didea.config.path=${Path.userHome}/.IdeaData/IDEA-14/scala/test-config",
  s"-Dplugin.path=${baseDirectory.value}/out/plugin/"
)

fullClasspath in Test := (fullClasspath in (SBT, Test)).value

baseDirectory in Test := baseDirectory.value.getParentFile

// packaging

pack in Compile <<= (pack in Compile) dependsOn (
  pack in (SBT, Compile),
  pack in (compiler_settings, Compile),
  pack in (intellij_hocon, Compile),
  pack in (intellij_scalastyle, Compile),
  pack in (jps_plugin, Compile),
  pack in (Runners, Compile),
  pack in (NailgunRunners, Compile),
  pack in (ScalaRunner, Compile)
  )

mappings in (Compile, packageBin) ++=
    mappings.in(intellij_hocon, Compile, packageBin).value ++
    mappings.in(intellij_scalastyle, Compile, packageBin).value ++
    mappings.in(SBT, Compile, packageBin).value

packageStructure in Compile := {
  lazy val resolved = (
    (dependencyClasspath in Compile).value ++
      (dependencyClasspath in(Runners, Compile)).value ++
      (dependencyClasspath in(ScalaCommunity, Compile)).value ++
      (dependencyClasspath in(intellij_hocon, Compile)).value
    )
    .map { f => f.metadata.get(moduleID.key) -> f.data}.toMap
    .collect { case (Some(x), y) => (x.organization % x.name % x.revision) -> y}
  def simplify(lib: ModuleID) = lib.organization % lib.name % lib.revision
  def libOf(lib: ModuleID, prefix: String = "lib/") = resolved(simplify(lib)) -> (prefix + resolved(simplify(lib)).name)
  Seq(
    (artifactPath in (ScalaCommunity, Compile, packageBin)).value    -> "lib/scala-plugin.jar",
    (artifactPath in (compiler_settings, Compile, packageBin)).value -> "lib/compiler-settings.jar",
    (artifactPath in (NailgunRunners, Compile, packageBin)).value    -> "lib/scala-nailgun-runner.jar",
    merge(
      (artifactPath in (Runners, Compile, packageBin)).value,
      (artifactPath in (ScalaRunner, Compile, packageBin)).value
    ) -> "lib/scala-plugin-runners.jar",
    file("SBT/jars") -> "launcher/",
    file("SDK/nailgun") -> "lib/jps/",
    file("SDK/sbt") -> "lib/jps/",
    file("SDK/scalap") -> "lib/",
    file("intellij-scalastyle/jars") -> "lib/",
    (artifactPath in (jps_plugin, Compile, packageBin)).value -> "lib/jps/scala-jps-plugin.jar",
    libOf("org.atteo" % "evo-inflector" % "1.2"),
    libOf("org.scala-lang" % "scala-library" % "2.11.2")._1 -> "lib/scala-library.jar",
    libOf("org.scala-lang" % "scala-library" % "2.11.2"),
    libOf("org.scala-lang" % "scala-reflect" % "2.11.2"),
    libOf("org.scalatest" % "scalatest-finders" % "0.9.6"),
    libOf("org.scala-lang.modules" % "scala-xml_2.11" % "1.0.2"),
    libOf("org.scala-lang.modules" % "scala-parser-combinators_2.11" % "1.0.2")
  ) ++
    (libraryDependencies in SBT).value.map(libOf(_))
}

packagePlugin in Compile := {
  val (dirs, files) = (packageStructure in Compile).value.partition(_._1.isDirectory)
  val base = baseDirectory.value / "out" / "plugin"
  IO.delete(base)
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
