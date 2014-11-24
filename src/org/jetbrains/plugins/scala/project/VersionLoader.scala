package org.jetbrains.plugins.scala.project

import java.io.IOException

import scala.io.Source
import scala.util.control.Exception._

/**
 * @author Pavel Fatin
 */
object VersionLoader {
  private val RepositoryPage = "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/"

  private val ReleaseVersionLine = ".+>(\\d+\\.\\d+\\.\\d+)/<.*".r

  private val MinVersin = Version("2.8.0")

  private val HardcodedVersions = Seq("2.8.2", "2.9.3", "2.10.4", "2.11.4")

  def loadScalaVersions: Array[String] =
    fetchScalaVersions.right.toOption
            .getOrElse(HardcodedVersions)
            .map(Version(_))
            .filter(_ >= MinVersin)
            .sorted(implicitly[Ordering[Version]].reverse)
            .map(_.number)
            .toArray

  private def fetchScalaVersions: Either[String, Seq[String]] = {
    fetchLinesFrom(RepositoryPage).right.map { lines =>
      lines.collect {
        case ReleaseVersionLine(number) => number
      }
    }
  }

  private def fetchLinesFrom(url: String): Either[String, Seq[String]] = {
    val source = Source.fromURL(url)

    catching(classOf[IOException])
            .andFinally(source.close())
            .either(source.getLines().toVector)
            .left.map(_.getMessage)
  }
}
