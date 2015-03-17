package org.jetbrains.plugins.scala.project

import com.intellij.util.net.HttpConfigurable

import scala.io.Source
import scala.util.Try

/**
 * @author Pavel Fatin
 */
object Versions {
  val DefaultScalaVersion = Scala.defaultVersion

  val DefaultSbtVersion = Sbt.defaultVersion

  private val ReleaseVersionLine = ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r

  def loadScalaVersions = loadVersionsOf(Scala)
  
  def loadSbtVersions = loadVersionsOf(Sbt)

  private def loadVersionsOf(entity: Entity): Array[String] =
    loadVersionsFrom(entity.url)
            .getOrElse(entity.hardcodedVersions)
            .map(Version(_))
            .filter(_ >= entity.minVersion)
            .sorted(implicitly[Ordering[Version]].reverse)
            .map(_.number)
            .toArray

  private def loadVersionsFrom(url: String): Try[Seq[String]] = {
    loadLinesFrom(url).map { lines =>
      lines.collect {
        case ReleaseVersionLine(number) => number
      }
    }
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
}

private case class Entity(url: String, minVersion: Version, hardcodedVersions: Seq[String]) {
  def defaultVersion: String = hardcodedVersions.last
}

private object Scala extends Entity("http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/",
  Version("2.8.0"), Seq("2.8.2", "2.9.3", "2.10.4", "2.11.5"))

private object Sbt extends Entity("http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/launcher/",
  Version("0.12.0"), Seq("0.12.4", "0.13.7"))
