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

unmanagedJars in Compile +=  file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

unmanagedSourceDirectories in Compile += baseDirectory.value /  "src"

unmanagedSourceDirectories in Test += baseDirectory.value /  "test"

unmanagedResourceDirectories in Compile += baseDirectory.value /  "resources"

def getBuildId(buildTypes: List[String], branch: String): String =  {
  import scala.xml._
  import java.io._
  for (bt <- buildTypes) {
    val tcUrl = s"http://teamcity.jetbrains.com/guestAuth/app/rest/builds/?locator=buildType:(id:$bt),branch:(name:${branch})"
    try {
      val buildId = XML.loadString(IO.readLinesURL(url(tcUrl)).mkString) \ "build" \ "@id"
      return buildId.text
    } catch {
      case e: IOException =>""
    }
  }
  ""
}

lazy val ideaVersion = taskKey[String]( "gets idea sdk version from file")

ideaVersion := { readIdeaPropery( "ideaVersion") }

lazy val ideaBasePath = "SDK/ideaSDK/idea-" + readIdeaPropery( "ideaVersion")

unmanagedJars in Compile ++= (baseDirectory.value / ideaBasePath / "lib" *  "*.jar").classpath

unmanagedJars in Compile ++=  {
  val basePluginsDir = baseDirectory.value / ideaBasePath /  "plugins"
  val baseDirectories =
    basePluginsDir / "copyright" /  "lib" +++
      basePluginsDir / "gradle" /  "lib" +++
      basePluginsDir / "android" /  "lib" +++
      basePluginsDir / "Groovy" /  "lib" +++
      basePluginsDir / "IntelliLang" /  "lib" +++
      basePluginsDir / "java-i18n" /  "lib" +++
      basePluginsDir / "maven" /  "lib" +++
      basePluginsDir / "junit" /  "lib" +++
      basePluginsDir / "properties" /  "lib"
  val customJars = baseDirectories *  "*.jar"
  customJars.classpath
}

unmanagedJars in Compile ++= (baseDirectory.value /  "SDK/scalap" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value /  "SDK/nailgun" * "*.jar").classpath

lazy val compiler_settings = project.in(file( "compiler-settings")).settings(compile <<= (compile in Compile)  dependsOn (downloadIdea in Compile))

lazy val ScalaRunner = project.in(file( "ScalaRunner"))

lazy val Runners = project.in(file( "Runners")).dependsOn(ScalaRunner)

lazy val ScalaCommunity = project.in(file("")).dependsOn(compiler_settings, Runners).aggregate(jps_plugin).settings(compile <<= (compile in Compile)  dependsOn (downloadIdea in Global))

lazy val intellij_hocon = Project( "intellij-hocon", file("intellij-hocon")).dependsOn(ScalaCommunity)

lazy val intellij_scalastyle  =
  Project("intellij-scalastyle", file( "intellij-scalastyle")).dependsOn(ScalaCommunity)

lazy val jps_plugin = Project( "scala-jps-plugin", file("jps-plugin")).dependsOn(compiler_settings)

lazy val idea_runner = Project( "idea-runner", file("idea-runner"))

lazy val NailgunRunners = project.in(file( "NailgunRunners")).dependsOn(ScalaRunner)

lazy val SBT = project.in(file( "SBT")).dependsOn(intellij_hocon, ScalaCommunity % "compile->compile;test->test")

lazy val downloadIdea = taskKey[Unit]( "downloads idea runtime")

downloadIdea in Global := {
    import sys.process._
    import scala.xml._
    val log = streams.value.log
    val ideaSDKPath = (baseDirectory.value / ideaBasePath).getParentFile
    val ideaArchiveName = ideaSDKPath.getAbsolutePath + s"/ideaSDK${ideaVersion.value}.arc"
    log.info("COMM: "  + ideaSDKPath.getAbsolutePath)
    if (!(baseDirectory.value / ideaBasePath).exists) {
      implicit class Regex(sc: StringContext) {
        def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
      }
      def downloadDep(from: String, to: String, extractPath: Option[File => Any] = None): Unit = {
        val fileTo = file(to)
        if (!fileTo.exists()) {
          log.info(s"downloading: $from")
          IO.download(url(from), fileTo)
          log.success(s"download of ${fileTo.getName()} finished")
        }
        extractPath match {
          case Some(func) => {
            log.info(s"extracting from archive ${fileTo.getName()}")
            if (!file(ideaBasePath).exists) func(fileTo)
            val dirTo = (ideaSDKPath.listFiles sortWith {
              _.lastModified > _.lastModified
            }).head
            log.info("renaming dir: " + dirTo.renameTo(file(ideaBasePath)))
            log.success("extract finished")
          }
          case None =>
        }
      }
      if (!ideaSDKPath.exists) ideaSDKPath.mkdirs
      val (extension, extractFun) = System.getProperty("os.name") match {
        case r"^Linux" => (".tar.gz", { _: File => s"tar xvfz $ideaArchiveName -C ${ideaSDKPath.getAbsolutePath}" !})
        case r"^Mac OS.*" => (".mac.zip", IO.unzip(_: File, ideaSDKPath))
        case r"^Windows.*" => (".win.zip", IO.unzip(_: File, ideaSDKPath))
        case other => throw new RuntimeException(s"OS $other is not supported")
      }
      val buildId = getBuildId(List("bt410"), s"idea/${ideaVersion.value}")
      if (buildId != "") {
        log.info(s"got build Id = $buildId")
        val reply = XML.loadString(IO.readLinesURL(url(s"http://teamcity.jetbrains.com/guestAuth/app/rest/builds/id:$buildId/artifacts")).mkString)
        val baseUrl = "http://teamcity.jetbrains.com"
        val ideaUrl = (reply \ "file" find { it: NodeSeq => (it \ "@name").text.endsWith(extension)}).get \ "content" \ "@href"
        val sourcesUrl = (reply \ "file" find { it: NodeSeq => (it \ "@name").text == "sources.zip"}).get \ "content" \ "@href"
        downloadDep(baseUrl + ideaUrl, ideaArchiveName, Some(extractFun))
        downloadDep(baseUrl + sourcesUrl, ideaBasePath + "/sources.zip")
      } else log.warn("COMM: failed to get ideaSDK build id, not downloading sdk")
    }
}

// tests

fork  := true

parallelExecution := false

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

//fullClasspath in (Test, run) := Seq()

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

mappings in (Compile, packageBin) ++= {
  val base = baseDirectory.value
  for {
    (file, rp) <- (base / "META-INF" * "*.xml") x relativeTo(base)
  } yield file -> rp
}

packageStructure in Compile := {
  Map(
    (artifactPath in (ScalaCommunity, Compile, packageBin)).value     -> "lib/scala-plugin.jar",
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
    (artifactPath in (jps_plugin, Compile, packageBin)).value -> "lib/jps/scala-jps-plugin.jar"
  ) ++ {
    val libs = Set(
      "evo-inflector", "hamcrest-core", "junit",  "mockito-core",
      "objenesis",  "scala-library", "scala-parser-combinators", "scala-reflect", "scala-xml",
      "scalacheck", "scalatest-finders", "scalatest", "test-interface", "utest", "utest-runner"
    )
    val classpath =
      (managedClasspath in(ScalaCommunity, Compile)).value ++
        (managedClasspath in(Runners, Compile)).value ++
        (managedClasspath in(intellij_hocon, Compile)).value
    (classpath map { f =>
      if (libs.exists(f.data.name.contains(_)))
        Some(f.data -> s"lib/${f.data.name}")
      else None
    }).flatten
  }
}

packagePlugin in Compile := {
  val (dirs, files) = (packageStructure in Compile).value.partition(_._1.isDirectory)
  val base = baseDirectory.value / "out" / "plugin"
  IO.delete(base)
  dirs  foreach { case (from, to) => IO.copyDirectory(from, base / to, overwrite = true) }
  files foreach { case (from, to) => IO.copyFile(from, base / to)}
}

packagePlugin in Compile <<= (packagePlugin in Compile) dependsOn (pack in Compile)