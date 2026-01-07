import sbt.io.IO
import sbt.{URL, url}

import scala.collection.mutable
import scala.util.Try
import scala.xml.{Elem, XML}

// Contains some duplicated code from <ultimate root>/project/teamcity/TeamCityAPI.scala.
// This is because I cannot get the build to work when I try to reference definitions within this object
// from the `teamcity` package sources in the Ultimate repo.
// This could be connected to the fact that packages within `project` directories are not explicitly supported by
// sbt and we might be relying on undefined behaviour.
// https://www.scala-sbt.org/1.x/docs/Organizing-Build.html#Organizing+the+build
// TODO: Try referencing this object from the `teamcity` package sources in the Ultimate repo in some way.
object TeamCityCommunityUtil {
  val TeamcityBaseUrl = "https://buildserver.labs.intellij.net"
  val RestBaseUrl = s"$TeamcityBaseUrl/guestAuth/app/rest"
  val BuildsBaseUrl = s"$RestBaseUrl/builds"
  val TRUNK_INSTALLERS = "ijplatform_master_Idea_Installers"
  val BUILD_TYPE_PATTERNS: Seq[String] =
    "ijplatform_IjPlatform%s_Idea_InstallersForEapRelease" ::
      "ijplatform_IjPlatform%s_Idea_Installers" ::
      "ijplatform_master_Idea_InstallersForEapRelease" ::
      "IDEA_Trunk_Installers" ::
      TRUNK_INSTALLERS ::
      Nil

  def getBuildIdForVersionSafe(ideaVersion: String): Try[Option[String]] = {
    Try {
      //231.1234.15 -> 231
      val majorVersion = ideaVersion.split('.').headOption.getOrElse("")
      val buildTypePatterns = BUILD_TYPE_PATTERNS.toStream
      val buildTypes = buildTypePatterns.map(_.format(majorVersion))
      val detectedBuildIds = buildTypes.map(detectBuildIdForBuildType(ideaVersion, _))
      val result =
        detectedBuildIds.flatMap(_.toOption).flatten.find(artifactExists)
          .orElse(detectedBuildIds.flatMap(_.get).find(artifactExists))
      result
    }
  }

  private def detectBuildIdForBuildType(ideaVersion: String, buildType: String): Try[Option[String]] = Try {
    val response = buildResponse(buildType, "number", ideaVersion)
    // limit number of builds to 1 - filters bogus build twins
    val isSuccess = (response \\ "build").take(1).filter(x => (x \\ "@status").text == "SUCCESS")
    val id = (isSuccess \\ "@id").text
    if (id != null && id.nonEmpty)
      Some(id)
    else
      None
  }

  private def buildResponse(
    buildType: String,
    key: String,
    value: String
  ): Elem =
    fetchTCApi(s"$BuildsBaseUrl/?locator=buildType:(id:$buildType),branch:(default:true),status:SUCCESS,$key:$value")

  private def artifactExists(buildId: String): Boolean = {
    val locator = new BuildLocator(BuildsBaseUrl + "/")
    val response = locator
      .id(buildId)
      .route("/artifacts/")
      .getXml
    val num = (response \ "@count").text.toInt
    num > 0
  }

  final class BuildLocator(baseUrl: String = s"$BuildsBaseUrl/?locator=") {
    private val builder = new mutable.StringBuilder(baseUrl)
    def toUrl: URL = url(builder.toString())
    def getXml: Elem = XML.load(toUrl)
    def route(suffix: String): BuildLocator = { builder.append(suffix); this }
    def add(key: String, value: String): BuildLocator = { builder.append(key).append(':').append(value).append(','); this }
    def buildTypeId(btid: String): BuildLocator = { builder.append("buildType:(").append(btid).append("),"); this }
    def id(value: String): BuildLocator = add("id", value)
    def number(number: String): BuildLocator = add("number", number)
    def branch(value: String): BuildLocator = add("branch", value)
    def status(value: String): BuildLocator = add("status", value)
    def personal(value: Boolean): BuildLocator = add("personal", value.toString)
    def count(num: Int): BuildLocator = add("count", num.toString)
    def defaultFilter(value: Boolean): BuildLocator = add("defaultFilter", value.toString)
    def failedToStart(value: Boolean): BuildLocator = add("failedToStart", value.toString)
  }

  private def fetchTCApi(restUrl: String): Elem =
    XML.load(url(restUrl))

  def fetchPreviousTestFailures(): Set[String] = {
    val prevBuildIds = fetchPreviousBuildIds()
    // We want only builds that ran through completely and still failed.
    // Canceled builds would have the status "UNKNOWN".
    // We have to set defaultFilter:false, so that we get builds from branches other than the main branch
    println(prevBuildIds)
    prevBuildIds
      .map(buildId => fetchTCApi(s"$RestBaseUrl/testOccurrences?locator=build:(id:$buildId),status:FAILURE,count:1000"))
      .ensuring(_.forall(_.attribute("nextHref").isEmpty))
      .map(failures => (failures \\ "testOccurrence").map(failure => failure \@ "name").toSet)
      .reduceOption(_ union _) // return all tests that failed in *all* retrieved builds
      .getOrElse(throw new Exception(s"No previously failing test build found"))
  }

  def fetchPreviousBuildIds(): Seq[Long] = {
    def getProperty(name: String): String = {
      val value = System.getProperty(name)
      if (value == null)
        throw new Exception(s"$name not specified")
      value
    }
    val buildType = getProperty("teamcity.buildType.id")
    val buildNumber = getProperty("build.number")

    println(s"Fetching previous build ids for buildType=$buildType, buildNumber=$buildNumber")

    val builds = new BuildLocator()
      .buildTypeId(buildType)
      .number(buildNumber)
      .failedToStart(false)
      .defaultFilter(false)
      .count(1000)
      .getXml
    assert(builds.attribute("nextHref").isEmpty)
    (builds \\ "build")
      .filter(build => (build \@ "state") == "finished")
      .map(build => build \@ "id")
      .map(_.toLong)
      .toList
  }
}
