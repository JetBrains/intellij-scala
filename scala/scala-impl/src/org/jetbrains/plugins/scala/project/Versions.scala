package org.jetbrains.plugins.scala
package project

import com.intellij.util.net.HttpConfigurable
import org.jetbrains.plugins.scala.ScalaBundle

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

        loadLinesFrom(url).fold(
          Function.const(hardcodedVersions),
          extractVersions(_, versionPattern)
        ).map {
          Version(_)
        }.filter {
          _ >= version
        }
    }

    private[this] def extractVersions(strings: Seq[String],
                                      pattern: Regex) =
      strings.collect {
        case pattern(number) => number
      }
  }

  case object Scala extends Kind(
    if (isInternalMode)
      ScalaCandidatesEntity :: DottyEntity :: Nil
    else
      ScalaEntity :: Nil
  )

  case object SBT extends Kind(
    Sbt1Entity :: Sbt013Entity :: Nil
  )

  def loadLinesFrom(location: String): Try[Seq[String]] =
    Try(HttpConfigurable.getInstance().openHttpConnection(location)).map { connection =>
      try {
        Source.fromInputStream(connection.getInputStream)
          .getLines()
          .toVector
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
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
      Scala_2_10.major + ".0",
      scalaVersion :: Scala_2_11.minor :: Scala_2_10.minor :: Nil
    )

    val Sbt013Entity: Entity = Entity(
      "https://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/",
      "0.13.5",
      sbtLatest_0_13 :: Nil
    )

    val Sbt1Entity: Entity = Entity(
      "https://dl.bintray.com/sbt/maven-releases/org/scala-sbt/sbt-launch/",
      "1.0.0",
      (sbtLatestVersion :: sbtLatest_1_0 :: Nil).distinct
    )

    val DottyEntity: Entity = Entity(
      s"https://repo1.maven.org/maven2/ch/epfl/lamp/dotty_${Scala_3_0.major}/",
      Scala_3_0.major + ".0",
      Scala_3_0.minor :: Nil,
      ".+>(\\d+\\.\\d+\\.\\d+(?:-\\w+)?)/<.*".r
    )

    val ScalaCandidatesEntity: Entity = ScalaEntity.copy(
      hardcodedVersions = Scala_2_13.minor :: ScalaEntity.hardcodedVersions,
      versionPattern = DottyEntity.versionPattern
    )
  }

}