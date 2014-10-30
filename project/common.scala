import sbt._
import Keys._

object common extends Build {
  lazy val ideaVersion = settingKey[String]("gets idea sdk version from file")
  lazy val ideaBasePath = settingKey[File]("path to idea SDK")

  lazy val ideaBaseJars = settingKey[Classpath]("")
  lazy val ideaICPluginJars = settingKey[Classpath]("")
  lazy val ideaIUPluginJars = settingKey[Classpath]("")
  lazy val allIdeaJars = settingKey[Classpath]("")

  lazy val packagePlugin = taskKey[Unit]("package scala plugin locally")
  lazy val packagePluginZip = taskKey[Unit]("package and compress scala plugin locally")
  lazy val packageStructure = taskKey[Seq[(File, String)]]("plugin artifact structure")

  lazy val downloadIdea = taskKey[Unit]("downloads idea runtime")
  lazy val ideaResolver = settingKey[IdeaResolver]("idea sdk resolver")
  case class TCArtifact(from: String, to: String, extractFun: Option[File => Any] = None, overwrite: Boolean = false)
  implicit def tuple2TCA(t: (String, String, Option[File => Any], Boolean)): TCArtifact = TCArtifact(t._1, t._2, t._3, t._4)
  implicit def tuple32TCA(t: (String, String, Option[File => Any])): TCArtifact = TCArtifact(t._1, t._2, t._3, overwrite = false)
  implicit def tuple2TCA(t: (String, String, Boolean)): TCArtifact = TCArtifact(t._1, t._2, None, t._3)
  implicit def tuple2TCA(t: (String, String)): TCArtifact = TCArtifact(t._1, t._2, None, overwrite = false)
  case class IdeaResolver(teamcityURL: String, buildTypes: Seq[String], branch: String, artifacts: Seq[TCArtifact])
//def f =1
  // merge multiple jars in one and return it
  def merge(files: File*): File = {
    IO.withTemporaryDirectory({ tmp =>
      files.foreach(IO.unzip(_, tmp))
      val zipFile = IO.temporaryDirectory / "sbt-merge-result.jar"
      zipFile.delete()
      IO.zip((tmp ***) pair (relativeTo(tmp), false), zipFile)
      zipFile
    })
  }

  implicit class Regex(sc: StringContext) {
    def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }

  def getBuildId(resolver: IdeaResolver): Option[String] = {
    import scala.xml._
    (resolver.buildTypes flatMap { bt: String =>
      val tcUrl = resolver.teamcityURL + s"/guestAuth/app/rest/builds/?locator=buildType:(id:$bt),branch:(default:any,name:${resolver.branch})"
      print(s"trying: $tcUrl -> ")
      try {
        val buildId = (XML.loadString(IO.readLinesURL(url(tcUrl)).mkString) \ "build" \\ "@id").head
        println(if(buildId.nonEmpty) s"[$buildId]" else "Not Found")
        if (buildId.nonEmpty) Some(buildId.text) else None
      } catch {
        case e: Throwable => None
      }
    }).headOption
  }
}