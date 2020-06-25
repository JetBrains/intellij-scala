package org.jetbrains.sbt
package resolvers

import java.io.{File, FilenameFilter}

import org.jetbrains.annotations.NonNls

import scala.xml.XML

/**
 * @author Nikolay Obedin
 * @since 8/15/14.
 */
class SbtIvyCacheIndexer(val cacheDir: File) {

  def artifacts: Stream[ArtifactInfo] = listArtifacts(cacheDir)

  private val ivyFileFilter = new FilenameFilter {
    override def accept(dir: File, @NonNls name: String): Boolean = name.endsWith(".xml")
  }

  private def listArtifacts(dir: File): Stream[ArtifactInfo] = {
    if (!dir.isDirectory)
      throw InvalidRepository(dir.getAbsolutePath)
    val artifactsHere = dir.listFiles(ivyFileFilter).flatMap(extractArtifact).toStream
    artifactsHere ++ dir.listFiles.toStream.filter(_.isDirectory).flatMap(listArtifacts)
  }

  private def extractArtifact(ivyFile: File): Option[ArtifactInfo] = {
    try {
      val xml = XML.loadFile(ivyFile)
      val group    = (xml \\ "ivy-module" \\ "info" \\ "@organisation").text
      val artifact = (xml \\ "ivy-module" \\ "info" \\ "@module").text
      val version  = (xml \\ "ivy-module" \\ "info" \\ "@revision").text
      Some(ArtifactInfo(group, artifact, version))
    } catch {
      case _: Throwable => None
    }
  }
}

