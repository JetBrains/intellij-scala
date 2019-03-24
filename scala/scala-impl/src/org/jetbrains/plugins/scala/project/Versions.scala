package org.jetbrains.plugins.scala
package project

import com.intellij.util.net.HttpConfigurable
import org.jetbrains.sbt.Sbt

import scala.io.Source
import scala.util.{Try, matching}

/**
 * @author Pavel Fatin
 */
object Versions {

  import Entity._

  def loadScalaVersions(prefix: String = ""): (Array[String], String) =
    loadVersionsOf(prefix + "Scala", Scala)

  def loadSbtVersions(): (Array[String], String) =
    loadVersionsOf("sbt", Sbt013, Sbt1)

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

  private def loadVersionsOf(platformName: String,
                             entities: Entity*) = {
    val allVersions = extensions.withProgressSynchronously(s"Fetching $platformName versions") {
      entities.flatMap {
        case Entity(url, pattern, minVersion, hardcodedVersions) =>
          loadLinesFrom(url).toOption
            .fold(hardcodedVersions) { lines =>
              lines.collect {
                case pattern(number) => Version(number)
              }
            }.filter { version =>
            version >= minVersion
          }
      }
    }

    (
      allVersions.sorted.reverse.map(_.presentation).toArray,
      entities.last.hardcodedVersions.last.presentation
    )
  }

  private case class Entity(url: String,
                            pattern: matching.Regex,
                            minVersion: Version,
                            hardcodedVersions: Seq[Version])

  private object Entity {

    val Scala = Entity(
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
      ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r,
      Version("2.10.0"),
      Seq(
        "2.10.7",
        "2.11.12",
        buildinfo.BuildInfo.scalaVersion
      ).map(Version.apply)
    )

    val Sbt013 = Entity(
      "https://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/",
      ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r,
      Version("0.13.5"),
      Seq(Sbt.Latest_0_13)
    )

    val Sbt1 = Entity(
      "https://dl.bintray.com/sbt/maven-releases/org/scala-sbt/sbt-launch/",
      ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r,
      Version("1.0.0"),
      Seq(Sbt.Latest_1_0, Sbt.LatestVersion).distinct
    )

    val Dotty = Entity(
      "https://repo1.maven.org/maven2/ch/epfl/lamp/dotty_0.2/",
      """.+>(\d+.\d+.+)/<.*""".r,
      Version("0.2.0"),
      Seq(Version("0.2.0"))
    )
  }

}