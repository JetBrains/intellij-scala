package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.service.JpsServiceManager

import scala.jdk.CollectionConverters._

/**
 * @author Pavel Fatin
 */
abstract class ChunkExclusionService {
  def isExcluded(chunk: ModuleChunk): Boolean
}

object ChunkExclusionService {
  def isExcluded(chunk: ModuleChunk): Boolean = {
    val providers = JpsServiceManager.getInstance.getExtensions(classOf[ChunkExclusionService]).asScala
    providers.exists(_.isExcluded(chunk))
  }
}
