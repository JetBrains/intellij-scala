package org.jetbrains.plugins.dotty.project.template

import com.intellij.util.net.HttpConfigurable
import org.jdom.{Content, Element}
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.plugins.scala.project.template.Downloader

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.Try

/**
  * @author adkozlov
  */
object DottyDownloader extends Downloader {
  final val RepositoryUrl = "https://oss.jfrog.org/artifactory/oss-snapshot-local"
  final val GroupId = "me.d-d"
  final val ArtifactId = "dotty_2.11"
  final val DefaultRevision = "0.1-SNAPSHOT"

  def downloadDotty(version: String, listener: String => Unit) = download(version, listener)

  private def collectDependencies(pomFileUrl: String): Seq[Dependency] = {
    def toDependency(content: Content): Option[Dependency] = content match {
      case e: Element if e.getName == "dependency" =>
        Try(Dependency(
          e.getChild("groupId").getText,
          e.getChild("artifactId").getText,
          e.getChild("version").getText,
          Option(e.getChild("scope")).map(_.getText)
        )).toOption
      case _ => None
    }
    val element = Try(HttpConfigurable.getInstance().openHttpConnection(pomFileUrl)).map { connection =>
      try {
        val bytes = Source.fromInputStream(connection.getInputStream).mkString.getBytes
        MavenJDOMUtil.read(bytes, null)
      } finally {
        connection.disconnect()
      }
    }
    element.toOption.toSeq.flatMap(_.getDescendants.asScala).flatMap(toDependency _).toVector
  }

  override protected def sbtCommandsFor(version: String) = Seq(
    s"""set resolvers := Seq("JFrog OSS Snapshots" at "$RepositoryUrl")""",
    s"""set libraryDependencies := Seq("$GroupId" % "$ArtifactId" % "$version")"""
  ) ++ super.sbtCommandsFor(version)
}

private case class Dependency(groupId: String, artifactId: String, version: String, scope: Option[String])
