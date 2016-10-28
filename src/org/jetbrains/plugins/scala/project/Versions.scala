package org.jetbrains.plugins.scala.project

import com.intellij.util.net.HttpConfigurable

import scala.io.Source
import scala.util.Try
import scala.util.matching.Regex

/**
 * @author Pavel Fatin
 */
object Versions extends Versions {
  val DefaultScalaVersion: String = Scala.defaultVersion

  val DefaultSbtVersion: String = Sbt.defaultVersion

  override protected val releaseVersionLine: Regex = ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r

  def loadScalaVersions: Array[String] = loadVersionsOf(Scala)

  def loadSbtVersions: Array[String] = loadVersionsOf(Sbt)

  private object Scala extends Entity("http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
    Version("2.8.0"), Seq("2.8.2", "2.9.3", "2.10.6", "2.11.8"))

  private object Sbt extends Entity("https://dl.bintray.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/",
    Version("0.12.0"), Seq("0.12.4", "0.13.13"))
}

trait Versions {
  protected val releaseVersionLine: Regex

  protected def loadVersionsOf(entity: Entity): Array[String] = {
    loadVersionsFrom(entity.url, {
      case releaseVersionLine(number) => number
    })
      .getOrElse(entity.hardcodedVersions)
      .map(Version(_))
      .filter(_ >= entity.minVersion)
      .sorted(implicitly[Ordering[Version]].reverse)
      .map(_.number)
      .toArray
  }

  protected def loadVersionsFrom(url: String, filter: PartialFunction[String, String]): Try[Seq[String]] = {
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

  protected case class Entity(url: String, minVersion: Version, hardcodedVersions: Seq[String]) {
    def defaultVersion: String = hardcodedVersions.last
  }
}