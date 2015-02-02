package org.jetbrains.plugins.scala.project

import java.io.IOException

import com.intellij.util.net.HttpConfigurable

import scala.io.Source

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
    loadVersionsFrom(entity.url).right.toOption
            .getOrElse(entity.hardcodedVersions)
            .map(Version(_))
            .filter(_ >= entity.minVersion)
            .sorted(implicitly[Ordering[Version]].reverse)
            .map(_.number)
            .toArray

  private def loadVersionsFrom(url: String): Either[String, Seq[String]] = {
    loadLinesFrom(url).right.map { lines =>
      lines.collect {
        case ReleaseVersionLine(number) => number
      }
    }
  }

  private def loadLinesFrom(url: String): Either[String, Seq[String]] = {
    try {
      val connection = HttpConfigurable.getInstance().openHttpConnection(url)
      try {
        val source = Source.fromInputStream(connection.getInputStream)
        Right(source.getLines().toVector)
      } finally {
        connection.disconnect()
      }
    } catch {
      case exc : IOException => Left(exc.getMessage)
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
