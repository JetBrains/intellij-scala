package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.service.JpsServiceManager

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
abstract class SourceDependenciesProviderService {
  def getSourceDependenciesFor(chunk: ModuleChunk): Seq[JpsModule]
}

object SourceDependenciesProviderService {
  def getSourceDependenciesFor(chunk: ModuleChunk): Seq[JpsModule] = {
    val providers = JpsServiceManager.getInstance.getExtensions(classOf[SourceDependenciesProviderService]).asScala
    providers.flatMap(_.getSourceDependenciesFor(chunk)).toSeq
  }
}
