package org.jetbrains.plugins.scala
package project

import com.intellij.util.net.HttpConfigurable
import org.jetbrains.plugins.scala.LatestScalaVersions._

import scala.io.Source
import scala.util.Try
import scala.util.matching.Regex

/**
  * @author Pavel Fatin
  */
case class Versions(defaultVersion: String,
                    versions: Array[String])

object Versions {

  import Entity._

  sealed abstract class Kind(private[Versions] val entities: List[Entity]) {

    // TODO: do not do any IO in `apply` method!
    final def apply(): Versions = {
      val versions = extensions
        .withProgressSynchronously(ScalaBundle.message("title.fetching.available.this.versions", this))(loadVersions())
        .sorted
        .reverse
        .map(_.presentation)
        .toArray

      val Entity(_, _, defaultVersion :: _, _) :: _ = entities
      Versions(defaultVersion, versions)
    }

    private[this] def loadVersions(): Seq[Version] = entities.flatMap {
      case Entity(url, minVersion, hardcodedVersions, versionPattern) =>

        val version = Version(minVersion)

        val lines = loadLinesFrom(url)
        val versionStrings = lines.fold(
          Function.const(hardcodedVersions),
          extractVersions(_, versionPattern)
        )
        val versions = versionStrings.map(Version(_))
        versions.filter(_ >= version)
    }

    private[this] def extractVersions(strings: Seq[String],
                                      pattern: Regex) =
      strings.collect {
        case pattern(number) => number
      }
  }

  case object Scala extends Kind(
    if (isInternalMode)
      ScalaCandidatesEntity :: Nil
    else
      ScalaEntity :: Nil
  )

  case object SBT extends Kind(
    Sbt1Entity :: Sbt013Entity :: Nil
  )

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

  private[this] case class Entity(url: String,
                                  minVersion: String,
                                  hardcodedVersions: List[String],
                                  versionPattern: Regex = ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r)

  private[this] object Entity {

    import buildinfo.BuildInfo._

    val ScalaEntity: Entity = Entity(
      url = "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
      minVersion = Scala_2_10.major + ".0",
      hardcodedVersions = Scala_2_13.minor :: Scala_2_12.minor :: Scala_2_11.minor :: Scala_2_10.minor :: Nil
    )

    val ScalaCandidatesEntity: Entity = ScalaEntity.copy(
      versionPattern = ".+>(\\d+\\.\\d+\\.\\d+(?:-\\w+)?)/<.*".r
    )

    val Sbt013Entity: Entity = Entity(
      url = "https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/",
      minVersion = "0.13.18",
      hardcodedVersions = sbtLatest_0_13 :: Nil
    )

    val Sbt1Entity: Entity = Entity(
      url = "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/",
      minVersion = "1.4.1",
      hardcodedVersions = (sbtLatestVersion :: sbtLatest_1_0 :: Nil).distinct
    )
  }
}