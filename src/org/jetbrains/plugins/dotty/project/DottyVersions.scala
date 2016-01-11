package org.jetbrains.plugins.dotty.project

import java.io.File

import org.jetbrains.plugins.scala.project.template.FileExt
import org.jetbrains.plugins.scala.project.{Version, Versions}
import org.jetbrains.sbt.project.template.activator.ActivatorDownloadUtil._

/**
  * @author adkozlov
  */
object DottyVersions extends Versions {
  val DottyVersion = Dotty.defaultVersion

  private final val RepositoryUrl = "https://oss.jfrog.org/artifactory/oss-snapshot-local/me/d-d/dotty-bootstrapped/0.1-SNAPSHOT"

  private val SnapshotVersionLine = ".+>(dotty-bootstrapped-0.1)-(\\d+.\\d+)-(\\d+).jar<.*".r

  def loadDottyVersions = loadVersionsOf(Dotty, { case SnapshotVersionLine(_, _, number) => "0.1." + number })

  def loadCompilerTo(version: String, path: File) = {
    val jarName = loadVersionsFrom(RepositoryUrl, {
      case SnapshotVersionLine(prefix, date, number) if version.endsWith(number) => s"$prefix-$date-$number.jar"
    }).get.head

    downloadContentToFile(null, s"$RepositoryUrl/$jarName", path / s"dotty-compiler-$version.jar")
  }

  private object Dotty extends Entity(RepositoryUrl, Version("0.1.1"), Seq("0.1.1"))
}