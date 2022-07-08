package org.jetbrains.jps.incremental.scala.sources

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.scala.SourceDependenciesProviderService
import org.jetbrains.jps.model.module.{JpsModule, JpsModuleDependency}

import scala.jdk.CollectionConverters._

class SharedSourceDependenciesProviderService extends SourceDependenciesProviderService {
  override def getSourceDependenciesFor(chunk: ModuleChunk): Seq[JpsModule] = {
    val modules = chunk.getModules.asScala.toSeq

    val dependencies = modules.flatMap(_.getDependenciesList.getDependencies.asScala)

    dependencies.collect {
      case it: JpsModuleDependency if it.getModule != null && it.getModule.getModuleType == SharedSourcesModuleType.INSTANCE => it.getModule
    }
  }
}
