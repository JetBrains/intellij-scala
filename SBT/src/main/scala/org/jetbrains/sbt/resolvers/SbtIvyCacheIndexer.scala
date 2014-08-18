package org.jetbrains.sbt
package resolvers

import java.io.File

import org.apache.maven.index.ArtifactInfo

import scala.collection.mutable
import scala.xml.XML

/**
 * @author Nikolay Obedin
 * @since 8/15/14.
 */
class SbtIvyCacheIndexer(val cacheDir: File) {

  def foreach(fn: (ArtifactInfo => Unit)): Unit = {
    val queue: mutable.Queue[File] = mutable.Queue(cacheDir)
    while (queue.nonEmpty) {
      val currentEntry = queue.dequeue()
      if (currentEntry.isDirectory) {
        currentEntry.listFiles().foreach { queue.enqueue(_) }
      } else if (currentEntry.isFile && currentEntry.getName.endsWith(".xml")) {
        extractArtifact(currentEntry) foreach fn
      }
    }
  }

  def extractArtifact(ivyFile: File): Option[ArtifactInfo] = {
    try {
      val xml = XML.loadFile(ivyFile)
      val group    = (xml \\ "ivy-module" \\ "info" \\ "@organisation").text
      val artifact = (xml \\ "ivy-module" \\ "info" \\ "@module").text
      val version  = (xml \\ "ivy-module" \\ "info" \\ "@revision").text
      Some(new ArtifactInfo("", group, artifact, version, ""))
    } catch {
      case e : Throwable => None
    }
  }
}

