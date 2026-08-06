package org.jetbrains.jps.incremental.scala

import java.util.Collections

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.service.JpsServiceManager

import scala.jdk.CollectionConverters._

abstract class SourceDependenciesProviderService {
  def getSourceDependenciesFor(chunk: ModuleChunk): Seq[JpsModule]
}

object SourceDependenciesProviderService {
  def getSourceDependenciesFor(chunk: ModuleChunk): Seq[JpsModule] = {
    val providers = JpsServiceManager.getInstance.getExtensions(classOf[SourceDependenciesProviderService]).asScala
    providers.flatMap(_.getSourceDependenciesFor(chunk)).toSeq
  }

  def getSourceDependenciesFor(target: ModuleBuildTarget): Seq[JpsModule] = {
    getSourceDependenciesFor(new ModuleChunk(Collections.singleton(target)))
  }
}
