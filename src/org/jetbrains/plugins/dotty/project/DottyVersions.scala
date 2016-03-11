package org.jetbrains.plugins.dotty.project

import org.jetbrains.plugins.dotty.project.template.DottyDownloader._
import org.jetbrains.plugins.scala.project.{Version, Versions}

/**
  * @author adkozlov
  */
object DottyVersions extends Versions {
  val DottyVersion = Dotty.defaultVersion

  override protected val releaseVersionLine = """.+>(\d+.\d+.+)/<.*""".r

  def loadDottyVersions = loadVersionsOf(Dotty)

  private object Dotty extends Entity(s"$RepositoryUrl/${GroupId.replace('.', '/')}/$ArtifactId",
    Version(DefaultRevision), Seq(DefaultRevision))
}