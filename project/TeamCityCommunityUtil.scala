import sbt.io.IO
import sbt.{URL, url}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node, XML}

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
    def state(value: String): BuildLocator = add("state", value)
    def personal(value: Boolean): BuildLocator = add("personal", value.toString)
    def count(num: Int): BuildLocator = add("count", num.toString)
    def defaultFilter(value: Boolean): BuildLocator = add("defaultFilter", value.toString)
    def failedToStart(value: Boolean): BuildLocator = add("failedToStart", value.toString)
  }

  private def fetchTCApi(restUrl: String): Elem =
    XML.load(url(restUrl))

  case class PreviousTests(failing: Set[String], passing: Set[String])

  /**
   * A test bucket is started by a snapshot dependency, so its direct trigger is usually `snapshotDependency`.<br>
   * Follow that dependency chain to determine whether the root build was started by TeamCity's retry trigger.
   *
   * This deliberately uses the structured `triggered.type` REST field instead of the
   * human-readable `teamcity.build.triggeredBy` parameter.
   *
   * If the origin cannot be read, it returns false: a manually restarted build must never be treated as an automatic retry.
   */
  def isCurrentBuildAnAutomaticRetry: Boolean = {
    val teamcityBuildId = Option(System.getProperty("teamcity.build.id")).filter(_.nonEmpty)
    teamcityBuildId.exists(isBuildInAutomaticRetryChainSafeCheck)
  }

  private def isBuildInAutomaticRetryChainSafeCheck(buildId: String): Boolean = {
    val result = Try(isBuildInAutomaticRetryChain(buildId))
    result match {
      case Success(value) => value
      case Failure(ex) =>
        println(s"Couldn't determine whether build $buildId is an automatic retry: $ex")
        ex.printStackTrace()
        false
    }
  }

  /**
   * Returns true only when this build, or a build that caused it to run, has TeamCity's automatic `retry` trigger type.
   * A manually started build has the `user` trigger type, so a manual retry is not treated as an automatic retry.
   *
   * TeamCity documentation:
   *   - [[https://www.jetbrains.com/help/teamcity/rest/build.html#triggered]]
   *   - [[https://www.jetbrains.com/help/teamcity/rest/triggeredby.html#type]]
   *   - [[https://www.jetbrains.com/help/teamcity/rest/triggeredby.html#build]]
   *   - [[https://www.jetbrains.com/help/teamcity/rest/build.html#id]]
   *   - [[https://www.jetbrains.com/help/teamcity/rest/teamcity-rest-api-documentation.html#Full+and+Partial+Responses]]
   *   - [[https://www.jetbrains.com/help/teamcity/predefined-build-parameters.html#Predefined+Server+Build+Parameters]]
   *   - [[https://www.jetbrains.com/help/teamcity/predefined-build-parameters.html#Other+Parameters]]
   */
  private def isBuildInAutomaticRetryChain(initialBuildId: String): Boolean = {
    @tailrec
    def loop(buildId: String, visited: Set[String]): Boolean = {
      if (visited.contains(buildId))
        false
      else {
        val build = fetchTCApi(s"$BuildsBaseUrl/id:$buildId?fields=id,triggered(type,build(id))")
        val triggered = (build \ "triggered").headOption
        val triggerType = triggered.map(_ \@ "type")
        if (triggerType.contains("retry"))
          true
        else {
          val parentBuildId = triggered
            .flatMap(node => (node \ "build").headOption)
            .map(_ \@ "id")
            .filter(_.nonEmpty)

          parentBuildId match {
            case Some(parentId) =>
              loop(parentId, visited + buildId)
            case _ =>
              false
          }
        }
      }
    }

    loop(initialBuildId, Set.empty)
  }

  def fetchPreviousTests(): PreviousTests = {
    val prevBuildIds = fetchPreviousBuildIds()
    println(s"Previous build Ids: [${prevBuildIds.mkString(", ")}]")
    val results = prevBuildIds
      .map(buildId => fetchTCApi(s"$RestBaseUrl/testOccurrences?locator=build:(id:$buildId),count:100000"))
      .ensuring(_.forall(_.attribute("nextHref").isEmpty)) // check that there are not more than 100000 test occurrences
      .map { tests =>
        val (passing, failing) =
          (tests \\ "testOccurrence")
            .partition(test => (test \@ "status") == "SUCCESS")
        def name(node: Node): String = node \@ "name"
        (passing.map(name).toSet, failing.map(name).toSet)
      }

    val (passing, failing) =
      results
      .reduceOption[(Set[String], Set[String])] {
        case ((aPassing, aFailing), (bPassing, bFailing)) =>
          (aPassing union bPassing, aFailing intersect bFailing)
      } // return all tests that failed in *all* retrieved builds
      .getOrElse(throw new Exception(s"No previously failing test build found"))

    for (testName <- failing.iterator) {
      if (!isExpectedTestName(testName))
        throw new Exception(s"Cannot handle testName '$testName'")
    }

    PreviousTests(failing, passing)
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

    // We want only builds that ran through completely and still failed.
    // Canceled builds would have the status "UNKNOWN".
    // We have to set defaultFilter:false, so that we get builds from branches other than the main branch
    val builds = new BuildLocator()
      .buildTypeId(buildType)
      .number(buildNumber)
      .failedToStart(false)
      .defaultFilter(false)
      .status("FAILURE")
      .state("finished")
      .count(1000) // only get a thousand builds... we expect to get at most 10 or so.
      .getXml
    // If more builds are returned nextHref will be set and we'll fail here
    assert(builds.attribute("nextHref").isEmpty)
    (builds \\ "build")
      .map(build => build \@ "id")
      .map(_.toLong)
      .toList
  }

  // All our tests actually start with one of these prefixes
  // So if we don't see one of these prefixes on a failing test, we are not able to handle it and should rerun all tests.
  // For example, we might have only one failing test with the name "Check after <NUM> Diff", that indicated that no
  // tests were run last time... obviously in this case we should run all tests.
  private val expectedTestNamePrefixes =
    Array(
      "org.jetbrains.",
      "com.intellij.",
      "scala.meta.",
      "CompilerPluginTest_", // don't question it!!!
      "AfterUpdateDottyVersionScript",
    )

  def isExpectedTestName(testName: String): Boolean =
    expectedTestNamePrefixes.exists(testName.startsWith)


  /**
   * Escapes a string that should occur between the ' in a tc service message.
   * {{{
   *  '       -> |'
   *  [       -> |[
   *  ]       -> |]
   *  |       -> ||
   *  newline -> |n
   * }}}
   */
  def escapeTCServiceMessageString(str: String): String =
    str.replaceAll(raw"([|'\[\]])", "|$1")
      .replaceAll("\n", "|n")
}
