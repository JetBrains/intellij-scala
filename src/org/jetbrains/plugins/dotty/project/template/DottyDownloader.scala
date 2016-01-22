package org.jetbrains.plugins.dotty.project.template

import org.jetbrains.plugins.scala.project.template.Downloader

/**
  * @author adkozlov
  */
object DottyDownloader extends Downloader {
  final val RepositoryUrl = "https://oss.jfrog.org/artifactory/oss-snapshot-local"
  final val GroupId = "me.d-d"
  final val ArtifactId = "dotty_2.11"
  final val DefaultRevision = "0.1-SNAPSHOT"

  def downloadDotty(version: String, listener: String => Unit) = download(version, listener)

  override protected def sbtCommandsFor(version: String) = Seq(
    s"""set resolvers := Seq("JFrog OSS Snapshots" at "$RepositoryUrl")""",
    s"""set libraryDependencies := Seq("$GroupId" % "$ArtifactId" % "$version")"""
  ) ++ super.sbtCommandsFor(version)
}
