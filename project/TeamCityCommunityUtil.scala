import org.jetbrains.sbtidea.PluginLogger as log
import sbt.io.IO
import sbt.{URL, url}

import scala.collection.mutable
import scala.xml.{Elem, XML}

// Contains some duplicated code from <ultimate root>/project/teamcity/TeamCityAPI.scala.
// This is because I cannot get the build to work when I try to reference definitions within this object
// from the `teamcity` package sources in the Ultimate repo.
// This could be connected to the fact that packages within `project` directories are not explicitly supported by
// sbt and we might be relying on undefined behaviour.
// https://www.scala-sbt.org/1.x/docs/Organizing-Build.html#Organizing+the+build
// TODO: Try referencing this object from the `teamcity` package sources in the Ultimate repo in some way.
object TeamCityCommunityUtil {
  private val TeamcityBaseUrl = "https://buildserver.labs.intellij.net"
  private val RestBaseUrl = s"$TeamcityBaseUrl/guestAuth/app/rest"
  private val BuildsBaseUrl = s"$RestBaseUrl/builds"
  private val TRUNK_INSTALLERS = "ijplatform_master_Idea_Installers"
  private val BUILD_TYPE_PATTERNS: Seq[String] =
    "ijplatform_IjPlatform%s_Idea_InstallersForEapRelease" ::
      "ijplatform_IjPlatform%s_Idea_Installers" ::
      "ijplatform_master_Idea_InstallersForEapRelease" ::
      "IDEA_Trunk_Installers" ::
      TRUNK_INSTALLERS ::
      Nil

  def getBuildIdForVersionSafe(ideaVersion: String): Option[String] = {
    //231.1234.15 -> 231
    val majorVersion = ideaVersion.split('.').headOption.getOrElse("")
    val buildTypePatterns = BUILD_TYPE_PATTERNS.toStream
    val buildTypes = buildTypePatterns.map(_.format(majorVersion))
    val detectedBuildIds = buildTypes.flatMap(detectBuildIdForBuildType(ideaVersion, _))
    val result = detectedBuildIds.find(artifactExists)
    result
  }

  private def detectBuildIdForBuildType(ideaVersion: String, buildType: String): Option[String] = {
    try {
      val response = buildResponse(buildType, "number", ideaVersion)
      // limit number of builds to 1 - filters bogus build twins
      val isSuccess = (response \\ "build").take(1).filter(x => (x \\ "@status").text == "SUCCESS")
      val id = (isSuccess \\ "@id").text
      if (id != null && id.nonEmpty)
        Some(id)
      else
        None
    } catch {
      case _: Throwable =>
        None
    }
  }

  private def buildResponse(buildType: String,
                                  key: String, value: String) = {
    val from = url(s"$BuildsBaseUrl/?locator=buildType:(id:$buildType),branch:(default:true),status:SUCCESS,$key:$value")
    val string = IO.readLinesURL(from).mkString
    XML.loadString(string)
  }

  private def artifactExists(buildId: String): Boolean = {
    try {
      val locator = new BuildLocator(BuildsBaseUrl + "/")
      val response = locator
        .id(buildId)
        .route("/artifacts/")
        .getXml
      val num = (response \ "@count").text.toInt
      num > 0
    } catch {
      case e: Exception =>
        log.warn(s"Failed to get artifact info for build $buildId: ${e.printStackTrace()}")
        false
    }
  }

  private final class BuildLocator(baseUrl: String = s"$BuildsBaseUrl/?locator=") {
    private val builder = new mutable.StringBuilder(baseUrl)
    def toUrl: URL = url(builder.toString())
    def getXml: Elem = XML.load(toUrl)
    def route(suffix: String): BuildLocator = { builder.append(suffix); this }
    def add(key: String, value: String): BuildLocator = { builder.append(key).append(':').append(value).append(','); this }
    def buildTypeId(btid: String): BuildLocator = { builder.append("buildType:(").append(btid).append("),"); this }
    def id(value: String): BuildLocator = add("id", value)
    def branch(value: String): BuildLocator = add("branch", value)
    def status(value: String): BuildLocator = add("status", value)
    def personal(value: Boolean): BuildLocator = add("personal", value.toString)
    def count(num: Int): BuildLocator = add("count", num.toString)
  }
}
