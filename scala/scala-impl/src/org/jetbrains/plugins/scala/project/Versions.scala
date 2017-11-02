package org.jetbrains.plugins.scala.project

import com.intellij.util.net.HttpConfigurable
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.project.Platform.{Dotty, Scala}

import scala.io.Source
import scala.util.Try
import scala.util.matching.Regex

/**
 * @author Pavel Fatin
 */
object Versions  {
  val DefaultScalaVersion: String = Entity.Scala.defaultVersion

  val DefaultDottyVersion: String = Entity.Dotty.defaultVersion

  val DefaultSbtVersion: String = Entity.Sbt1.defaultVersion

  def loadScalaVersions(platform: Platform): Array[String] = platform match {
    case Scala => loadVersionsOf(Entity.Scala)
    case Dotty => loadVersionsOf(Entity.Dotty)
  }

  def loadSbtVersions: Array[String] = loadVersionsOf(Entity.Sbt013, Entity.Sbt1)

  private def loadVersionsOf(entities: Entity*): Array[String] = {
    val allVersions = entities.flatMap { entity =>
      val loaded = loadVersionsFrom(entity.url, {
        case entity.pattern(number) => number
      })

    loaded
      .getOrElse(entity.hardcodedVersions)
      .map(Version(_))
      .filter(_ >= entity.minVersion)
    }
    allVersions
      .sortWith(_ >= _)
      .map(_.presentation)
      .toArray
  }

  private def loadVersionsFrom(url: String, filter: PartialFunction[String, String]): Try[Seq[String]] = {
    loadLinesFrom(url).map { lines => lines.collect(filter) }
  }

  private def loadLinesFrom(url: String): Try[Seq[String]] = {
    Try(HttpConfigurable.getInstance().openHttpConnection(url)).map { connection =>
      try {
        Source.fromInputStream(connection.getInputStream).getLines().toVector
      } finally {
        connection.disconnect()
      }
    }
  }

  private case class Entity(url: String, pattern: Regex, minVersion: Version, hardcodedVersions: Seq[String]) {
    def defaultVersion: String = hardcodedVersions.last
  }

  private object Entity {
    val Scala = Entity("http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
      ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r,
      Version("2.10.0"),
      Seq("2.10.6", "2.11.11", "2.12.4"))

    val Sbt013 = Entity("https://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/",
      ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r,
      Version("0.13.5"),
      Seq(BuildInfo.sbtLatestVersion))

    val Sbt1 = Entity("https://dl.bintray.com/sbt/maven-releases/org/scala-sbt/sbt-launch/",
      ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r,
      Version("1.0.0"),
      Seq(BuildInfo.sbtLatestVersion))

    val Dotty = Entity("https://repo1.maven.org/maven2/ch/epfl/lamp/dotty_0.2/",
      """.+>(\d+.\d+.+)/<.*""".r,
      Version("0.2.0"),
      Seq("0.2.0-RC1"))
  }
}