package org.jetbrains.plugins.scala
package project

import com.intellij.util.net.HttpConfigurable

import scala.io.Source
import scala.util.Try

/**
 * @author Pavel Fatin
 */
case class Versions(defaultVersion: String,
                    versions: Array[String])

object Versions {

  import Entity._

  sealed abstract class Kind {

    private[Versions] val entities: Seq[Entity]

    final def apply(): Versions = {
      val versions = extensions
        .withProgressSynchronously(s"Fetching available $this versions")(loadVersions())
        .sorted
        .reverse
        .map(_.presentation)

      Versions(
        entities.last.hardcodedVersions.last,
        versions.toArray
      )
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

    private[this] def extractVersions(strings: Seq[String], pattern: String) = {
      val regex = pattern.r
      strings.collect {
        case regex(number) => number
      }
    }
  }

  case object ScalaKind extends Kind {
    override private[Versions] val entities: Seq[Entity] =
      if (isInternal) Seq(DottyEntity, ScalaEntity)
      else Seq(ScalaEntity)
  }

  case object SbtKind extends Kind {
    override private[Versions] val entities: Seq[Entity] =
      Seq(Sbt013Entity, Sbt1Entity)
  }

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
                                  hardcodedVersions: Seq[String],
                                  versionPattern: String = ".+>(\\d+\\.\\d+\\.\\d+)/<.*")

  private[this] object Entity {

    import buildinfo.BuildInfo._
    import debugger.{Scala_2_10, Scala_2_11, Scala_3_0}

    val ScalaEntity = Entity(
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
      Scala_2_10.major + ".0",
      Seq(Scala_2_10.minor, Scala_2_11.minor, scalaVersion)
    )

    val Sbt013Entity = Entity(
      "https://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/",
      "0.13.5",
      Seq(sbtLatest_0_13)
    )

    val Sbt1Entity = Entity(
      "https://dl.bintray.com/sbt/maven-releases/org/scala-sbt/sbt-launch/",
      "1.0.0",
      Seq(sbtLatest_1_0, sbtLatestVersion).distinct
    )

    val DottyEntity = Entity(
      s"https://repo1.maven.org/maven2/ch/epfl/lamp/dotty_${Scala_3_0.major}/",
      Scala_3_0.major + ".0",
      Seq(Scala_3_0.minor),
      """.+>(\d+.\d+.+)/<.*"""
    )
  }

}