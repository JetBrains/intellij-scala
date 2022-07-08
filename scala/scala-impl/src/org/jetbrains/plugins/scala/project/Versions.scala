package org.jetbrains.plugins.scala
package project

import com.intellij.util.net.HttpConfigurable
import org.jetbrains.plugins.scala.LatestScalaVersions._
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.extensions.withProgressSynchronously

import scala.io.Source
import scala.util.Try
import scala.util.matching.Regex

case class Versions(defaultVersion: String,
                    versions: Seq[String])

object Versions {

  import Entity._

  sealed abstract class Kind(private[Versions] val entities: List[Entity]) {

    final def loadVersionsWithProgress(): Versions = {
      val loaded = withProgressSynchronously(ScalaBundle.message("title.fetching.available.this.versions", this)) {
        entities.flatMap(loadVersions)
      }
      val versions = loaded
        .sorted
        .reverse
        .map(_.presentation)

      Versions(defaultVersion, versions)
    }

    protected def defaultVersion: String =
      entities.head.hardcodedVersions.head

    def initiallySelectedVersion(versions: Seq[String]): String =
      versions.headOption.getOrElse(defaultVersion)
  }

  case object Scala extends Kind(
    if (isInternalMode)
      Scala3CandidatesEntity :: ScalaCandidatesEntity :: Nil
    else
      Scala3Entity :: ScalaEntity :: Nil
  ) {

    // While Scala 3 support is WIP we do not want preselect Scala 3 version
    override protected def defaultVersion: String = {
      val scala2Entity = entities.find(v => isScala2Version(v.minVersion))
      scala2Entity.get.minVersion
    }

    // While Scala 3 support is WIP we do not want preselect Scala 3 version
    override def initiallySelectedVersion(versions: Seq[String]): String =
      versions.find(isScala2Version).getOrElse(defaultVersion)

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

  // TODO: this should not be a part of a Versions object
  def loadLinesFrom(url: String): Try[Seq[String]] =
    Try(HttpConfigurable.getInstance().openHttpConnection(url)).map { connection =>
      try {
        val lines = Source.fromInputStream(connection.getInputStream).getLines().toVector
        lines
      } finally {
        connection.disconnect()
      }
    }

  private def loadVersions(entity: Entity): Seq[Version] = {
    entity match {
      case DownloadableEntity(url, minVersionStr, hardcodedVersions, versionPattern) =>
        val minVersion = Version(minVersionStr)

        val lines = loadLinesFrom(url)
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

  def loadScala2Versions(): Seq[Version] = loadVersions(ScalaEntity)

  def loadSbt1Versions(): Seq[Version] = loadVersions(Sbt1Entity)

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
      hardcodedVersions = Scala_2_13.minor :: Scala_2_12.minor :: Scala_2_11.minor :: Scala_2_10.minor :: Nil
    )
    val Scala3Entity: DownloadableEntity = DownloadableEntity(
      url = "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/",
      minVersion = Scala_3_0.major + ".0",
      hardcodedVersions = Scala_3_1.minor :: Scala_3_0.minor :: Nil
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
      url = "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/",
      minVersion = "1.0.0",
      hardcodedVersions = (BuildInfo.sbtLatestVersion :: BuildInfo.sbtLatest_1_0 :: Nil).distinct
    )
  }
}