package org.jetbrains.plugins.scala.project

import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.LatestScalaVersions._
import org.jetbrains.plugins.scala.extensions.withProgressSynchronously
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion, isInternalMode}
import org.jetbrains.sbt.{MinorVersionGenerator, SbtVersion, SbtVersionCapabilities}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters.ScalaDurationOps
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object Versions {

  import Entity._

  sealed abstract class Kind(private[Versions] val entities: List[Entity]) {

    final def loadVersionsWithProgressDialog(): Seq[Version] = {
      val cancelable = true
      val loaded = withProgressSynchronously(ScalaBundle.message("title.fetching.available.this.versions", this), canBeCanceled = cancelable) {
        loadVersionsSorted(entities, cancelable, ProgressManager.getInstance().getProgressIndicator, propagateDownloadExceptions = false)
      }
      loaded.sorted.reverse
    }

    @RequiresBackgroundThread
    final def loadVersionsWithProgress(indicator: ProgressIndicator): Seq[Version] = {
      loadVersionsSorted(entities, cancelable = true, indicator, propagateDownloadExceptions = true)
    }

    /**
     * The method chooses the version to select by default from the given list of versions.
     * The selected value is used in a New Project Wizard in the Scala/sbt version list.
     *
     * @param versions list of a version to select from. Doesn't have to be ordered
     * @return the version to select in a new project wizard by default<br>
     *         Returns [[None]] when the version list is empty or if it's not possible to select a preferred version
     */
    def initiallySelectedVersion(versions: Seq[String]): Option[String]

    lazy val allHardcodedVersions: Seq[Version] = {
      val versions = entities
        .flatMap {
          case DownloadableEntity(_, minVersionStr, hardcodedVersions, _) =>
            val minVersion = Version(minVersionStr)
            hardcodedVersions
              .map(Version(_))
              .filter(_ >= minVersion)
          case StaticEntity(_, hardcodedVersions) =>
            hardcodedVersions.map(Version.apply)
        }
      versions.sorted.reverse
    }
  }

  case object Scala extends Kind(
    if (isInternalMode)
      Scala3EntityWithCandidates :: ScalaEntityWithCandidates :: Nil
    else
      Scala3Entity :: ScalaEntity :: Nil
  ) {

    override def initiallySelectedVersion(versions: Seq[String]): Option[String] =
      latestLTSVersion(versions)

    private def latestLTSVersion(versions: Seq[String]): Option[String] = {
      // checking the language level because the list can contain multiple versions with different minor suffix (3.3.1, 3.3.2)
      val ltsVersions = versions.flatMap(ScalaVersion.fromString).filter(_.languageLevel == ScalaVersion.Latest.Scala_3_LTS.languageLevel)
      ltsVersions.maxOption.map(_.minor)
    }
  }

  case object SBT extends Kind(
    if (isInternalMode)
      SbtEntityWithCandidates :: Sbt013Entity :: Nil
    else
      SbtEntity :: Nil
  ) {
    val LatestSbtVersion: String = SbtVersion.Latest.Sbt_1.minor

    override def initiallySelectedVersion(versions: Seq[String]): Option[String] = {
      latestStableVersion(versions)
    }

    private def latestStableVersion(versions: Seq[String]): Option[String] = {
      val stableVersions = versions.map(Version(_)).filterNot(_.presentation.contains("-"))
      stableVersions.maxOption.map(_.presentation)
    }

    def sbtVersionsForScala3(sbtVersions: Seq[SbtVersion]): Seq[SbtVersion] = {
      val minVersion = SbtVersionCapabilities.MinSbtVersionForScala3
      sbtVersions.filter(_ >= minVersion)
    }
  }

  @RequiresBackgroundThread
  private def loadVersionsSorted(
    entities: Seq[Entity],
    cancelable: Boolean,
    indicator: ProgressIndicator,
    propagateDownloadExceptions: Boolean,
    timeout: FiniteDuration = 10.seconds
  ): Seq[Version] = {
    val client = HttpClient.newBuilder().executor(AppExecutorUtil.getAppExecutorService).build()

    val (downloadable, static) = entities.partitionMap {
      case d@DownloadableEntity(_, _, _, _) => Left(d)
      case s@StaticEntity(_, _) => Right(s)
    }

    val latch = new CountDownLatch(downloadable.size)

    val httpFutures: Seq[CompletableFuture[HttpResponse[java.util.stream.Stream[String]]]] = downloadable.map {
      case DownloadableEntity(url, _, _, _) =>
        val request = HttpRequest.newBuilder(URI.create(url))
          .version(HttpClient.Version.HTTP_1_1).timeout(timeout.toJava).build()
        client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
    }

    val downloadedVersionStringsFutures = downloadable.zip(httpFutures).map {
      case (entity@DownloadableEntity(_, _, hardcodedVersions, versionPattern), future) =>
        future
          .thenApply[Seq[String]](_.body().toList.asScala.toSeq)
          .thenApply[(DownloadableEntity, Seq[String])] { lines =>
            val versionStrings =
              if (lines.isEmpty) hardcodedVersions
              else extractVersions(lines, versionPattern)

            entity -> versionStrings
          }
          .whenComplete((_, _) => latch.countDown())
    }

    val downloadedVersionStringsTry = Try {
      while (!latch.await(300L, TimeUnit.MILLISECONDS)) {
        if (cancelable && indicator.isCanceled) {
          httpFutures.foreach(_.cancel(true))
          indicator.checkCanceled()
        }
      }

      downloadedVersionStringsFutures.map(_.get())
    }

    val downloadedVersionStrings = downloadedVersionStringsTry match {
      case Failure(exception) if propagateDownloadExceptions => throw exception
      case Failure(_) => downloadable.map(d => d -> d.hardcodedVersions)
      case Success(downloadedVersions) => downloadedVersions
    }

    val versionsDownloaded: Seq[Version] = downloadedVersionStrings.flatMap {
      case (DownloadableEntity(_, minVersionStr, _, _), versionStrings) =>
        val minVersion = Version(minVersionStr)
        val versions = versionStrings.map(Version(_))
        versions.filter(_ >= minVersion)
    }

    val versionsStatic = static.flatMap { case StaticEntity(_, hardcodedVersions) => hardcodedVersions.map(Version.apply) }
    val versionsAll = versionsDownloaded ++ versionsStatic
    val versionsWithoutOldCandidates = removeOldCandidateVersionsForEachMajor(versionsAll)
    versionsWithoutOldCandidates.sorted.reverse
  }

  /**
   * This method is needed in the internal mode when we also detect release candidate versions.
   *
   * This filtering ensures that we don't show old RC versions that are not needed in practice.
   * During testing, in internal mode, we are primarily interested in the latest UPCOMING RC versions.
   *
   * It leaves only the latest candidate version for every major version (first two numbers separated with dot)
   * only if this latest candidate is newer than the latest stable version matching the same major version.
   *
   * Example: {{{
   *   Input  : [2.13.17-RC1, 2.13.16, 2.13.15-M1, 2.12.21-M1, 2.12.20, 2.12.18-M2, 2.11.9, 2.11.9-M2, 2.11.8]
   *   Output : [2.13.17-RC1, 2.13.16, 2.12.21-M1, 2.12.20, 2.11.9, 2.11.8]
   * }}}
   */
  @Internal
  @TestOnly
  def removeOldCandidateVersionsForEachMajor(versions: Seq[Version]): Seq[Version] = {
    val versionsByMajor = versions.groupBy(_.major(2))
    versionsByMajor.values.flatMap(removeOldCandidateVersions).toSeq
  }

  private def removeOldCandidateVersions(majorVersions: Seq[Version]): Seq[Version] = {
    val (candidates, stable) = majorVersions.partition(_.presentation.contains("-"))

    val latestStable = stable.maxOption
    val latestCandidate = candidates.maxOption

    // use the latest candidate version only if it's newer than the latest stable or if there is no stable version
    val latestCandidateActual = latestCandidate.filter(v => latestStable.forall(_ < v))
    stable ++ latestCandidateActual.toSeq
  }

  private def extractVersions(strings: Seq[String], pattern: Regex) =
    strings.collect {
      case pattern(number) => number
    }

  @RequiresBackgroundThread
  def loadScala2Versions(canBeCanceled: Boolean, indicator: ProgressIndicator): Seq[Version] = loadVersionsSorted(Seq(ScalaEntity), canBeCanceled, indicator, propagateDownloadExceptions = true)
  lazy val scala2HardcodedVersions: List[String] = ScalaEntity.hardcodedVersions

  @RequiresBackgroundThread
  def loadSbtVersions(canBeCanceled: Boolean, indicator: ProgressIndicator): Seq[SbtVersion] = loadVersionsSorted(Seq(SbtEntity), canBeCanceled, indicator, propagateDownloadExceptions = true).map(SbtVersion(_))
  lazy val sbtHardcodedVersions: Seq[String] = SbtEntity.hardcodedVersions

  lazy val scala3HardcodedVersions: List[String] = Scala3Entity.hardcodedVersions

  private sealed trait Entity {
    def minVersion: String
    def hardcodedVersions: List[String]
  }

  /**
   * @param url               url of the resource that contains the list of versions for the entity (usually in XML or HTML format)
   * @param minVersion        version used to filter out too old versions (e.g., scala 2.9)
   * @param hardcodedVersions the fallback list of hardcoded versions which is calculated based on the hardcoded
   *                          information about the latest sbt versions
   * @param versionPattern    pattern used to detect a version in resource at [[url]]
   */
  private case class DownloadableEntity(
    url: String,
    override val minVersion: String,
    override val hardcodedVersions: List[String],
    versionPattern: Regex
  ) extends Entity

  private case class StaticEntity(
    override val minVersion: String,
    override val hardcodedVersions: List[String]
  ) extends Entity

  private object Entity {
    private val ThreeDigitsVersionPattern: Regex =
    """(\d+\.\d+\.\d+)""".r
    private val ThreeDigitsVersionPatternWithCandidates: Regex =
    """(\d+\.\d+\.\d+(?:-\w+)?)""".r

    private val ScalaVersionItemPattern: Regex =
      s".+>$ThreeDigitsVersionPattern/<.*".r
    private val ScalaVersionItemPatternWithCandidates: Regex =
      s""".+>$ThreeDigitsVersionPatternWithCandidates/<.*""".r

    private val SbtVersionPattern: Regex =
      s"""^\\s+<version>$ThreeDigitsVersionPattern</version>$$""".r
    private val SbtVersionPatternWithCandidates: Regex =
      s"""^\\s+<version>$ThreeDigitsVersionPatternWithCandidates</version>$$""".r

    val ScalaEntity: DownloadableEntity = DownloadableEntity(
      url = "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
      minVersion = Scala_2_10.major + ".0",
      hardcodedVersions = MinorVersionGenerator.generateAllMinorVersions(allScala2, (v: ScalaVersion) => v.minor),
      versionPattern = ScalaVersionItemPattern
    )
    val Scala3Entity: DownloadableEntity = DownloadableEntity(
      url = "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/",
      minVersion = Scala_3_0.major + ".0",
      hardcodedVersions = MinorVersionGenerator.generateAllMinorVersions(allScala3, (v: ScalaVersion) => v.minor),
      versionPattern = ScalaVersionItemPattern
    )

    val ScalaEntityWithCandidates: Entity = ScalaEntity.copy(versionPattern = ScalaVersionItemPatternWithCandidates)
    val Scala3EntityWithCandidates: Entity = Scala3Entity.copy(versionPattern = ScalaVersionItemPatternWithCandidates)

    // Do not download SBT 0.13.x versions from the internet, support only the latest 0.13 version
    // It also helps performance:
    // downloading of 0.13 version from the internet takes quite long due to some JFrog server issues
    val Sbt013Entity: StaticEntity = StaticEntity(
      SbtVersion.Latest.Sbt_0_13.minor,
      SbtVersion.Latest.Sbt_0_13.minor :: Nil
    )

    val SbtEntity: DownloadableEntity = DownloadableEntity(
      url = "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/maven-metadata.xml",
      minVersion = "1.0.0",
      hardcodedVersions = MinorVersionGenerator.generateAllMinorVersions(
        SbtVersion.Latest.AllSbt1 ++ SbtVersion.Latest.AllSbt2,
        (v: Version) => v.presentation
      ),
      versionPattern = SbtVersionPattern
    )
    val SbtEntityWithCandidates: Entity = SbtEntity.copy(versionPattern = SbtVersionPatternWithCandidates)
  }
}
