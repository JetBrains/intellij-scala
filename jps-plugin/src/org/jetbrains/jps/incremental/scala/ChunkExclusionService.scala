package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.service.JpsServiceManager
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
abstract class ChunkExclusionService {
  def isExcluded(chunk: ModuleChunk, global: JpsGlobal): Boolean
}

object ChunkExclusionService {
  def isExcluded(chunk: ModuleChunk, global: JpsGlobal): Boolean = {
    val providers = JpsServiceManager.getInstance.getExtensions(classOf[ChunkExclusionService]).asScala
    providers.exists(_.isExcluded(chunk, global: JpsGlobal))
  }
}
