package org.jetbrains.plugins.scala.project

import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.plugins.scala.LatestScalaVersions._
import org.jetbrains.plugins.scala.extensions.withProgressSynchronously
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion, isInternalMode}
import org.jetbrains.sbt.buildinfo.BuildInfo
import org.jetbrains.sbt.{MinorVersionGenerator, SbtVersion}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters.ScalaDurationOps
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

case class Versions(defaultVersion: String,
                    versions: Seq[String])

object Versions {

  import Entity._

  sealed abstract class Kind(private[Versions] val entities: List[Entity]) {

    final def loadVersionsWithProgressDialog(): Versions = {
      val cancelable = true
      val loaded = withProgressSynchronously(ScalaBundle.message("title.fetching.available.this.versions", this), canBeCanceled = cancelable) {
        loadVersions(entities, cancelable, ProgressManager.getInstance().getProgressIndicator, propagateDownloadExceptions = false)
      }
      mapVersionListToVersions(loaded)
    }

    @RequiresBackgroundThread
    final def loadVersionsWithProgress(indicator: ProgressIndicator): Versions = {
      val loaded = loadVersions(entities, cancelable = true, indicator, propagateDownloadExceptions = true)
      mapVersionListToVersions(loaded)
    }

    private def mapVersionListToVersions(versions: Seq[Version]): Versions = {
      val stringVersions = versions
        .sorted
        .reverse
        .map(_.presentation)
      Versions(defaultVersion, stringVersions)
    }

    def initiallySelectedVersion(versions: Seq[String]): String =
      versions.headOption.getOrElse(defaultVersion)

    lazy val allHardcodedVersions: Versions = {
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
      mapVersionListToVersions(versions)
    }

    protected def defaultVersion: String =
      entities.head.hardcodedVersions.head
  }

  case object Scala extends Kind(
    if (isInternalMode)
      Scala3CandidatesEntity :: ScalaCandidatesEntity :: Nil
    else
      Scala3Entity :: ScalaEntity :: Nil
  ) {

    // While Scala 3 support is WIP we do not want preselect Scala 3 version
    override protected def defaultVersion: String =
      ScalaVersion.Latest.Scala_3_LTS.minor

    override def initiallySelectedVersion(versions: Seq[String]): String = {
      //can contain multiple versions with different minor suffix (3.3.1, 3.3.2)
      val ltsVersions = versions.flatMap(ScalaVersion.fromString).filter(_.languageLevel == ScalaVersion.Latest.Scala_3_LTS.languageLevel)
      ltsVersions.maxOption.map(_.minor).getOrElse(defaultVersion)
    }

    private def isScala2Version(v: String) = v.startsWith("2")
  }

  case object SBT extends Kind(
    Sbt1Entity :: Sbt013Entity :: Nil
  ) {
    val LatestSbtVersion: String = BuildInfo.sbtLatestVersion

    /** Scala3 is only supported since sbt 1.5.0 */
    val MinSbtVersionForScala3 = "1.5.0"

    def sbtVersionsForScala3(sbtVersions: Versions): Versions = {
      val minVersion = Version(MinSbtVersionForScala3)
      Versions(LatestSbtVersion, sbtVersions.versions.collect { case v if Version(v) >= minVersion => v })
    }
  }

  @RequiresBackgroundThread
  private def loadVersions(
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
      case (d@DownloadableEntity(_, _, hardcodedVersions, versionPattern), future) =>
        future
          .thenApply[Seq[String]](_.body().toList.asScala.toSeq)
          .thenApply[(DownloadableEntity, Seq[String])] { lines =>
            val versionStrings =
              if (lines.isEmpty) hardcodedVersions
              else extractVersions(lines, versionPattern)

            d -> versionStrings
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

    val downloaded = downloadedVersionStrings.flatMap {
      case (DownloadableEntity(_, minVersionStr, _, _), versionStrings) =>
        val minVersion = Version(minVersionStr)
        val versions = versionStrings.map(Version(_))
        versions.filter(_ >= minVersion)
    }

    downloaded ++ static.flatMap { case StaticEntity(_, hardcodedVersions) => hardcodedVersions.map(Version.apply) }
  }

  private def extractVersions(strings: Seq[String], pattern: Regex) =
    strings.collect {
      case pattern(number) => number
    }

  @RequiresBackgroundThread
  def loadScala2Versions(canBeCanceled: Boolean, indicator: ProgressIndicator): Seq[Version] = loadVersions(Seq(ScalaEntity), canBeCanceled, indicator, propagateDownloadExceptions = true)
  lazy val scala2HardcodedVersions: List[String] = ScalaEntity.hardcodedVersions
  @RequiresBackgroundThread
  def loadSbt1Versions(canBeCanceled: Boolean, indicator: ProgressIndicator): Seq[Version] = loadVersions(Seq(Sbt1Entity), canBeCanceled, indicator, propagateDownloadExceptions = true)
  lazy val sbt1HardcodedVersions: Seq[String] = Sbt1Entity.hardcodedVersions

  private sealed trait Entity {
    def minVersion: String
    def hardcodedVersions: List[String]
  }

  private case class DownloadableEntity(
    url: String,
    override val minVersion: String,
    override val hardcodedVersions: List[String],
    versionPattern: Regex = ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r
  ) extends Entity

  private case class StaticEntity(
    override val minVersion: String,
    override val hardcodedVersions: List[String]
  ) extends Entity

  private object Entity {


    val ScalaEntity: DownloadableEntity = DownloadableEntity(
      url = "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
      minVersion = Scala_2_10.major + ".0",
      hardcodedVersions = MinorVersionGenerator.generateAllMinorVersions(allScala2, (v: ScalaVersion) => v.minor)
    )
    val Scala3Entity: DownloadableEntity = DownloadableEntity(
      url = "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/",
      minVersion = Scala_3_0.major + ".0",
      hardcodedVersions = MinorVersionGenerator.generateAllMinorVersions(allScala3, (v: ScalaVersion) => v.minor)
    )

    private val CandidateVersionPattern: Regex = ".+>(\\d+\\.\\d+\\.\\d+(?:-\\w+)?)/<.*".r
    val ScalaCandidatesEntity: Entity = ScalaEntity.copy(versionPattern = CandidateVersionPattern)
    val Scala3CandidatesEntity: Entity = Scala3Entity.copy(versionPattern = CandidateVersionPattern)

    //Do not download SBT 0.13.x versions from internet, support only latest 0.13 version
    //It also helps performance: downloading of 0.13 version from internet takes quite long due to some JFrog server issues
    val Sbt013Entity: StaticEntity = StaticEntity(
      BuildInfo.sbtLatest_0_13,
      BuildInfo.sbtLatest_0_13 :: Nil
    )

    val Sbt1Entity: DownloadableEntity = DownloadableEntity(
      url = "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/maven-metadata.xml",
      minVersion = "1.0.0",
      hardcodedVersions = MinorVersionGenerator.generateAllMinorVersions(SbtVersion.allSbt1, (v: Version) => v.presentation),
      versionPattern = """^\s+<version>(\d+\.\d+\.\d+)</version>$""".r
    )
  }
}