package org.jetbrains.plugins.scala.project

import java.net.HttpURLConnection

import com.intellij.util.net.HttpConfigurable
import org.jetbrains.plugins.hydra.compiler.HydraCredentialsManager
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

  def loadHydraVersions(repoURL: String, login: String, password: String): Array[String] = {
    val entity = Entity.Hydra
    val loadedVersions = loadVersionsForHydra(repoURL, login, password)
    val hydraVersions = filterVersionsForEntity(loadedVersions.getOrElse(entity.hardcodedVersions), entity)
    sortVersions(hydraVersions)
  }

  private def loadVersionsOf(entities: Entity*): Array[String] = {
    val allVersions = entities.flatMap { entity =>
      val loaded = loadVersionsFrom(entity.url, {
        case entity.pattern(number) => number
      })

      filterVersionsForEntity(loaded.getOrElse(entity.hardcodedVersions), entity)
    }
    sortVersions(allVersions)
  }

  private def filterVersionsForEntity(versions: Seq[String], entity: Entity) = {
    versions.map(Version(_)).filter(_  >= entity.minVersion)
  }

  private def sortVersions(versions: Seq[Version]) = {
    versions.sortWith(_ >= _).map(_.presentation).toArray
  }

  private def loadVersionsFrom(url: String, filter: PartialFunction[String, String]): Try[Seq[String]] = {
    loadLinesFrom(url).map { lines => lines.collect(filter) }
  }

  private def loadHydraVersionsFrom(url: String, login:String, password: String, filter: PartialFunction[String, String]): Try[Seq[String]] = {
    val loadedLines = loadLinesFrom(url){ connection => connection.setRequestProperty("Authorization", "Basic " + HydraCredentialsManager.getBasicAuthEncoding()) }
    loadedLines.map { lines => lines.collect(filter) }
  }

  private def loadLinesFrom(url: String)(implicit prepareConnection: HttpURLConnection => Unit = _ => ()): Try[Seq[String]] = {
    Try(HttpConfigurable.getInstance().openHttpConnection(url)).map { connection =>
      try {
        prepareConnection(connection)
        Source.fromInputStream(connection.getInputStream).getLines().toVector
      } finally {
        connection.disconnect()
      }
    }
  }

  private def loadVersionsForHydra(repoURL: String, login: String, password: String) = {
    val entity = Entity.Hydra
    val entityUrl = if (repoURL.endsWith("/")) repoURL + entity.url else repoURL + "/" + entity.url

    def downloadHydraVersions(url: String): Seq[String] =
      loadHydraVersionsFrom(url, login, password, { case entity.pattern(number) => number }).getOrElse(entity.hardcodedVersions).map(Version(_))
        .filter(_ >= entity.minVersion).map(_.presentation)

    loadHydraVersionsFrom(entityUrl, login, password, {
      case entity.pattern(number) => number
    }).map { versions =>
      versions.flatMap(version => downloadHydraVersions(s"""$entityUrl$version/""")).distinct
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

    val Hydra = Entity("ivy-releases/com.triplequote/",
      ".+>(.*\\d+\\.\\d+\\.\\d+.*)/<.*".r,
      Version("0.9.5"),
      Seq("0.9.5")
    )
  }
}