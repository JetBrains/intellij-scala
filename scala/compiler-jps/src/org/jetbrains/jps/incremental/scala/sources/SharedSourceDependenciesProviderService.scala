package org.jetbrains.jps.incremental.scala.sources

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.scala.{BuildParametersUtils, SourceDependenciesProviderService}
import org.jetbrains.jps.model.module.{JpsModule, JpsModuleDependency}

import scala.jdk.CollectionConverters._

class SharedSourceDependenciesProviderService extends SourceDependenciesProviderService {
  override def getSourceDependenciesFor(chunk: ModuleChunk): Seq[JpsModule] = {
    val modules = chunk.getModules.asScala.toSeq

    val dependencies = modules.flatMap(_.getDependenciesList.getDependencies.asScala)
    val sharedSourcesModules = dependencies.collect {
      case it: JpsModuleDependency if it.getModule != null && it.getModule.getModuleType == SharedSourcesModuleType.INSTANCE => it.getModule
    }

    if (areTransitiveDependenciesEnabled) {
      val representativeTargetName = chunk.representativeTarget().getId
      leaveOnlyRequiredSharedSourcesModules(sharedSourcesModules, representativeTargetName)
    } else {
      sharedSourcesModules
    }

  }

  private def leaveOnlyRequiredSharedSourcesModules(sharedSourcesModules: Seq[JpsModule], representativeTargetName: String): Seq[JpsModule] =
    sharedSourcesModules.filter { jpsModule =>
      val typedModule = jpsModule.asTyped(SharedSourcesModuleType.INSTANCE)
      if (typedModule != null) {
        typedModule.getProperties match {
          case x: SharedSourcesProperties => x.ownerModuleNames.contains(representativeTargetName)
          case _ => false
        }
      } else {
        false
      }
    }

  private def areTransitiveDependenciesEnabled: Boolean = {
    val shouldProcessDependenciesRecursively = BuildParametersUtils.getProcessDependenciesRecursivelyProperty
    !shouldProcessDependenciesRecursively
  }
}
