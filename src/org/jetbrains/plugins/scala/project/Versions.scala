package org.jetbrains.plugins.scala.project

import com.intellij.util.net.HttpConfigurable

import scala.io.Source
import scala.util.Try

/**
 * @author Pavel Fatin
 */
object Versions extends Versions {
  val DefaultScalaVersion = Scala.defaultVersion

  val DefaultSbtVersion = Sbt.defaultVersion

  private val ReleaseVersionLine = ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r

  def loadScalaVersions = loadVersionsOf(Scala)

  def loadSbtVersions = loadVersionsOf(Sbt)

  private def loadVersionsOf(entity: Entity): Array[String] = loadVersionsOf(entity, {
    case ReleaseVersionLine(number) => number
  })

  private object Scala extends Entity("http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
    Version("2.8.0"), Seq("2.8.2", "2.9.3", "2.10.4", "2.11.5"))

  private object Sbt extends Entity("http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/launcher/",
    Version("0.12.0"), Seq("0.12.4", "0.13.7"))
}

trait Versions {
  protected def loadVersionsOf(entity: Entity,
                               filter: PartialFunction[String, String]): Array[String] = {
    loadVersionsFrom(entity.url, filter)
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