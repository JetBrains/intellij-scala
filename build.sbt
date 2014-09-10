name := "ScalaCommunity"

organization := "JetBrains"

scalaVersion := "2.11.2"

libraryDependencies += "org.scalatest" % "scalatest-finders" % "0.9.6"

libraryDependencies += "org.atteo" % "evo-inflector" % "1.2"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.2"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

unmanagedSourceDirectories in Compile += baseDirectory.value / "src"

unmanagedSourceDirectories in Test += baseDirectory.value / "test"

unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"

val ideaBasePath = "SDK/ideaSDK/idea14"

unmanagedJars in Compile ++= (baseDirectory.value / ideaBasePath / "lib" * "*.jar").classpath

unmanagedJars in Compile ++= {
  val basePluginsDir = baseDirectory.value / ideaBasePath / "plugins"
  val baseDirectories =
    basePluginsDir / "copyright" / "lib" +++
      basePluginsDir / "gradle" / "lib" +++
      basePluginsDir / "Groovy" / "lib" +++
      basePluginsDir / "IntelliLang" / "lib" +++
      basePluginsDir / "java-i18n" / "lib" +++
      basePluginsDir / "maven" / "lib" +++
      basePluginsDir / "junit" / "lib" +++
      basePluginsDir / "properties" / "lib"
  val customJars = baseDirectories * "*.jar"
  customJars.classpath
}

unmanagedJars in Compile ++= (baseDirectory.value / "SDK/scalap" * "*.jar").classpath

unmanagedJars in Compile ++= (baseDirectory.value / "SDK/nailgun" * "*.jar").classpath

lazy val compiler_settings = Project("compiler-settings", file("compiler-settings"))

lazy val ScalaRunner = project.in(file("ScalaRunner"))

lazy val Runners = project.in(file("Runners")).dependsOn(ScalaRunner)

lazy val ScalaCommunity = project.in(file("")).dependsOn(compiler_settings, Runners).aggregate(jps_plugin)

lazy val intellij_hocon = Project("intellij-hocon", file("intellij-hocon")).dependsOn(ScalaCommunity)

lazy val intellij_scalastyle =
  Project("intellij-scalastyle", file("intellij-scalastyle")).dependsOn(ScalaCommunity)

lazy val jps_plugin = Project("scala-jps-plugin", file("jps-plugin")).dependsOn(compiler_settings)

lazy val idea_runner = Project("idea-runner", file("idea-runner"))

lazy val NailgunRunners = project.in(file("NailgunRunners")).dependsOn(ScalaRunner)

lazy val SBT = project.in(file("SBT")).dependsOn(intellij_hocon, ScalaCommunity)

lazy val ideaVersion = taskKey[String]("gets idea sdk version from file")

ideaVersion := {
  import java.util.Properties
  val log = streams.value.log
  val prop = new Properties()
  IO.load(prop, file("idea.properties"))
  prop.getProperty("ideaVersion")
}

lazy val downloadIdea = taskKey[Unit]("downloads idea runtime")

downloadIdea := {
    import sys.process._
    import scala.xml._
    val log = streams.value.log
    val ideaSDKPath = "SDK/ideaSDK/"
    val ideaArchiveName = "ideaSDK.arc"
    implicit class Regex(sc: StringContext) {
          def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
    }
    def downloadDep(from: String, to: String, extractPath: Option[File => Any] = None) = {
      val fileTo = file(to)
      if (!fileTo.exists()) {
        log.info(s"downloading: $from")
        IO.download(url(from), fileTo)
        log.success(s"download of ${fileTo.getName()} finished")
      }
      extractPath match { case Some(func) => {
          log.info(s"extracting from archive ${fileTo.getName()}")
          func(fileTo)
          log.success("extract finished")
        } case None =>
      }
    }
    val (extension, extractFun) = System.getProperty("os.name") match {
        case r"^Linux"    => (".tar.gz",  {_:File => s"tar xvfz $ideaArchiveName -C $ideaSDKPath"!})
        case r"^Mac OS.*" => (".mac.zip", IO.unzip(_:File, file(ideaSDKPath)))
        case r"^Windows.*"=> (".win.zip", IO.unzip(_:File, file(ideaSDKPath)))
        case other        => throw new RuntimeException(s"OS $other is not supported")
    }
    val buildId = XML.loadString(IO.readLinesURL(url(s"http://teamcity.jetbrains.com/guestAuth/app/rest/builds/?locator=buildType:(id:bt410),branch:(name:idea%2F${ideaVersion.value})")).mkString) \ "build" \ "@id"
    log.info(s"got build Id = $buildId")
    val reply = XML.loadString(IO.readLinesURL(url(s"http://teamcity.jetbrains.com/guestAuth/app/rest/builds/id:$buildId/artifacts")).mkString)
    val baseUrl = "http://teamcity.jetbrains.com"
    val ideaUrl = (reply \ "file" find {it:NodeSeq =>(it \ "@name").text.endsWith(extension)}).get \ "content" \ "@href" text
    val sourcesUrl = s"${baseUrl}sources.zip"
    downloadDep(baseUrl + ideaUrl, "ideaSDK.arc", Some(extractFun))
    downloadDep(sourcesUrl, "sources.zip")
}

update <<= (update) dependsOn downloadIdea
