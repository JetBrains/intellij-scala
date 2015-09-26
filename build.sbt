import Keys.{`package` => pack}

name :=  "ScalaCommunity"

organization :=  "JetBrains"

scalaVersion :=  "2.11.2"

resolvers in ThisBuild += "bintray" at "http://dl.bintray.com/jetbrains/maven-patched/"

libraryDependencies += "org.apache.maven.indexer" % "indexer-core" % "6.0"

//libraryDependencies += "org.apache.maven.indexer" % "indexer-artifact" % "5.1.2" % Compile - was merged with core in 6.0

libraryDependencies +=  "org.scalatest" % "scalatest-finders" % "0.9.6"

libraryDependencies +=  "org.atteo" % "evo-inflector" % "1.2"

libraryDependencies +=  "org.scala-lang.modules" %% "scala-xml" % "1.0.2"

libraryDependencies +=  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"

libraryDependencies +=  "org.scala-lang" % "scala-reflect" % scalaVersion.value

libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

libraryDependencies ++= Seq(
  "org.codehaus.plexus" % "plexus-container-default" % "1.5.5" % Compile,
  "org.sonatype.sisu" % "sisu-inject-plexus" % "2.2.3" % Compile,
  "org.apache.maven.wagon" % "wagon-http" % "2.6" % Compile
)

lazy val testDownloader = project.in(file("testJarsDownloader"))

update := {
  (update in testDownloader).value
  update.value
}

unmanagedSourceDirectories in Compile += baseDirectory.value /  "src"

unmanagedSourceDirectories in Test += baseDirectory.value /  "test"

unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"

javacOptions in Global ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions in Global += "-target:jvm-1.6"

ideaVersion := "141.2735.5"

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

lazy val compiler_settings = project.in(file( "compiler-settings"))
  .settings(unmanagedJars in Compile := allIdeaJars.value)

lazy val ScalaRunner = project.in(file( "ScalaRunner"))

lazy val Runners = project.in(file( "Runners")).dependsOn(ScalaRunner)

lazy val ScalaCommunity = project.in(file("")).dependsOn(compiler_settings, Runners).aggregate(jps_plugin)

lazy val jps_plugin = Project( "scala-jps-plugin", file("jps-plugin")).dependsOn(compiler_settings)
  .settings(unmanagedJars in Compile := allIdeaJars.value)

lazy val idea_runner = Project( "idea-runner", file("idea-runner"))
  .settings(unmanagedJars in Compile := (ideaBasePath.value  / "lib" * "*.jar").classpath, autoScalaLibrary := false)
  .dependsOn(Seq(compiler_settings, ScalaRunner, Runners, ScalaCommunity, jps_plugin, NailgunRunners).map(_ % Provided): _*)

lazy val NailgunRunners = project.in(file( "NailgunRunners")).dependsOn(ScalaRunner)

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
  s"-Didea.system.path=${Path.userHome}/.IdeaData/IDEA-14/scala/test-system",
  s"-Didea.config.path=${Path.userHome}/.IdeaData/IDEA-14/scala/test-config",
  s"-Dplugin.path=${baseDirectory.value}/out/plugin/Scala"
)

// jar hell workaround(ignore idea bundled lucene in test runtime)
fullClasspath in Test := {(fullClasspath in Test).value.filterNot(_.data.getName.endsWith("lucene-core-2.4.1.jar"))}

baseDirectory in Test := baseDirectory.value.getParentFile

// packaging

pack in Compile <<= (pack in Compile) dependsOn (
  pack in (compiler_settings, Compile),
  pack in (jps_plugin, Compile),
  pack in (Runners, Compile),
  pack in (NailgunRunners, Compile),
  pack in (ScalaRunner, Compile)
  )

packageStructure in Compile := {
  lazy val resolved = (
    (dependencyClasspath in Compile).value ++
      (dependencyClasspath in(Runners, Compile)).value ++
      (dependencyClasspath in(ScalaCommunity, Compile)).value
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
    file("jars") -> "launcher/",
    file("SDK/nailgun") -> "lib/jps/",
    file("SDK/sbt") -> "lib/jps/",
    file("SDK/scalap") -> "lib/",
    file("SDK/scalastyle") -> "lib/",
    (artifactPath in (jps_plugin, Compile, packageBin)).value -> "lib/jps/scala-jps-plugin.jar",
    libOf("org.atteo" % "evo-inflector" % "1.2"),
    libOf("org.scala-lang" % "scala-library" % "2.11.2")._1 -> "lib/scala-library.jar",
    libOf("org.scala-lang" % "scala-reflect" % "2.11.2"),
    libOf("org.scalatest" % "scalatest-finders" % "0.9.6"),
    libOf("org.scala-lang.modules" % "scala-xml_2.11" % "1.0.2"),
    libOf("org.scala-lang.modules" % "scala-parser-combinators_2.11" % "1.0.2"),
    libOf("org.apache.maven.indexer" % "indexer-core" % "6.0"),
    libOf("org.apache.maven" % "maven-model" % "3.0.5"),
    libOf("org.codehaus.plexus" % "plexus-container-default" % "1.5.5"),
    libOf("org.codehaus.plexus" % "plexus-classworlds" % "2.4"),
    libOf("org.codehaus.plexus" % "plexus-utils" % "3.0.8"),
    libOf("org.codehaus.plexus" % "plexus-component-annotations" % "1.5.5"),
    libOf("org.apache.lucene" % "lucene-core" % "4.3.0"),
    libOf("org.apache.lucene" % "lucene-highlighter" % "4.3.0"),
    libOf("org.apache.lucene" % "lucene-memory" % "4.3.0"),
    libOf("org.apache.lucene" % "lucene-queries" % "4.3.0"),
    libOf("org.eclipse.aether" % "aether-api" % "1.0.0.v20140518"),
    libOf("org.eclipse.aether" % "aether-util" % "1.0.0.v20140518"),
    libOf("org.sonatype.sisu" % "sisu-inject-plexus" % "2.2.3"),
    libOf("org.sonatype.sisu" % "sisu-inject-bean" % "2.2.3"),
    libOf("org.sonatype.sisu" % "sisu-guice" % "3.0.3"),
    libOf("org.apache.maven.wagon" % "wagon-http" % "2.6"),
    libOf("org.apache.maven.wagon" % "wagon-http-shared" % "2.6"),
    libOf("org.apache.maven.wagon" % "wagon-provider-api" % "2.6"),
    libOf( "org.apache.xbean" % "xbean-reflect" % "3.4"),
    libOf("org.jsoup" % "jsoup" % "1.7.2"),
    libOf("commons-lang" % "commons-lang" % "2.6"),
    libOf("commons-io" % "commons-io" % "2.2"),
    libOf("org.apache.httpcomponents" % "httpclient" % "4.3.1"),
    libOf("org.apache.httpcomponents" % "httpcore" % "4.3"),
    libOf("commons-logging" % "commons-logging" % "1.1.3"),
    libOf("commons-codec" % "commons-codec" % "1.6")
  )
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
