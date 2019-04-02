package org.jetbrains.plugins.scala
package project

import com.intellij.util.net.HttpConfigurable
import org.jetbrains.sbt.Sbt

import scala.io.Source
import scala.util.Try

/**
 * @author Pavel Fatin
 */
object Versions {

  import Entity._

  def loadScalaVersions(prefix: String = ""): (Array[String], String) =
    loadVersionsOf(prefix + "Scala", scalaEntities)

  def loadSbtVersions(): (Array[String], String) =
    loadVersionsOf("sbt", sbtEntities)

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

  private def loadVersionsOf(platformName: String, entities: Seq[Entity]) = {
    val allVersions = extensions.withProgressSynchronously(s"Fetching $platformName versions") {
      entities.flatMap(loadVersions)
    }.sorted.reverse

    (
      allVersions.map(_.presentation).toArray,
      entities.last.hardcodedVersions.last.presentation
    )
  }

  private case class Entity(url: String,
                            minVersionPresentation: String,
                            hardcodedVersions: Seq[Version],
                            maybePattern: Option[String] = None)

  private object Entity {

    def scalaEntities: Seq[Entity] = if (isInternal) Seq(Dotty, Scala) else Seq(Scala)

    def sbtEntities: Seq[Entity] = Seq(Sbt013, Sbt1)

    def loadVersions(entity: Entity): Seq[Version] = {
      val Entity(url, minVersionPresentation, hardcodedVersions, maybePattern) = entity

      val minVersion = Version(minVersionPresentation)
      val pattern = maybePattern.getOrElse(".+>(\\d+\\.\\d+\\.\\d+)/<.*").r

      loadLinesFrom(url).toOption
        .fold(hardcodedVersions) { lines =>
          lines.collect {
            case pattern(number) => Version(number)
          }
        }.filter {
        _ >= minVersion
      }
    }

    private val Scala = Entity(
      "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
      "2.10.0",
      Seq("2.10.7", "2.11.12", buildinfo.BuildInfo.scalaVersion).map(Version.apply)
    )

    private val Sbt013 = Entity(
      "https://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/",
      "0.13.5",
      Seq(Sbt.Latest_0_13)
    )

    private val Sbt1 = Entity(
      "https://dl.bintray.com/sbt/maven-releases/org/scala-sbt/sbt-launch/",
      "1.0.0",
      Seq(Sbt.Latest_1_0, Sbt.LatestVersion).distinct
    )

    private val Dotty = Entity(
      "https://repo1.maven.org/maven2/ch/epfl/lamp/dotty_0.13/",
      "0.13.0",
      Seq(Version("0.13.0-RC1")),
      Some(""".+>(\d+.\d+.+)/<.*""")
    )
  }

}