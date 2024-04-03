package org.jetbrains.plugins.scala.project

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.LatestScalaVersions._
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion, isInternalMode}
import org.jetbrains.plugins.scala.extensions.withProgressSynchronously
import org.jetbrains.plugins.scala.util.HttpDownloadUtil
import org.jetbrains.sbt.{SbtVersion, MinorVersionGenerator}
import org.jetbrains.sbt.buildinfo.BuildInfo

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Failure
import scala.util.matching.Regex

case class Versions(defaultVersion: String,
                    versions: Seq[String])

object Versions {

  import Entity._

  sealed abstract class Kind(private[Versions] val entities: List[Entity]) {

    final def loadVersionsWithProgressDialog(): Versions = {
      val cancelable = true
      val loaded = withProgressSynchronously(ScalaBundle.message("title.fetching.available.this.versions", this), canBeCanceled = cancelable) {
           entities.flatMap(loadVersions(_, cancelable, None, propagateDownloadExceptions = false))
      }
      mapVersionListToVersions(loaded)
    }

    final def loadVersionsWithProgress(indicator: ProgressIndicator): Versions = {
      val loaded = entities.flatMap(loadVersions(_, cancelable = true, Some(indicator), propagateDownloadExceptions = true))
      mapVersionListToVersions(loaded)
    }

    private def mapVersionListToVersions(versions: List[Version]): Versions = {
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

    def sbtVersionsForScala3(sbtVersions: Versions): Versions = Versions(
      LatestSbtVersion,
      sbtVersions.versions.filter(_ >= MinSbtVersionForScala3)
    )
  }

  private def loadVersions(
    entity: Entity,
    cancelable: Boolean,
    indicatorOpt: Option[ProgressIndicator],
    propagateDownloadExceptions: Boolean,
    timeout: FiniteDuration = 10.seconds
  ): Seq[Version] = {
    entity match {
      case DownloadableEntity(url, minVersionStr, hardcodedVersions, versionPattern) =>
        val minVersion = Version(minVersionStr)

        val lines = HttpDownloadUtil.loadLinesFrom(url, cancelable, indicatorOpt, timeout)
        lines match {
          case Failure(exc) if propagateDownloadExceptions => throw exc
          case _ =>
        }
        val versionStrings = lines.fold(
          Function.const(hardcodedVersions),
          extractVersions(_, versionPattern)
        )
        val versions = versionStrings.map(Version(_))
        versions.filter(_ >= minVersion)
      case StaticEntity(_, hardcodedVersions) =>
        hardcodedVersions.map(Version.apply)
    }
  }

  private def extractVersions(strings: Seq[String], pattern: Regex) =
    strings.collect {
      case pattern(number) => number
    }

  def loadScala2Versions(canBeCanceled: Boolean, indicatorOpt: Option[ProgressIndicator]): Seq[Version] = loadVersions(ScalaEntity, canBeCanceled, indicatorOpt, propagateDownloadExceptions = true)
  lazy val scala2HardcodedVersions: List[String] = ScalaEntity.hardcodedVersions
  def loadSbt1Versions(canBeCanceled: Boolean, indicatorOpt: Option[ProgressIndicator]): Seq[Version] = loadVersions(Sbt1Entity, canBeCanceled, indicatorOpt, propagateDownloadExceptions = true)
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