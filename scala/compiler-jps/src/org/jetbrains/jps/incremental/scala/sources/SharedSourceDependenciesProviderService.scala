package org.jetbrains.jps.incremental.scala.sources

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.scala.SourceDependenciesProviderService
import org.jetbrains.jps.model.module.{JpsModule, JpsModuleDependency}

import scala.jdk.CollectionConverters._

class SharedSourceDependenciesProviderService extends SourceDependenciesProviderService {
  override def getSourceDependenciesFor(chunk: ModuleChunk): Seq[JpsModule] = {
    val modules = chunk.getModules.asScala.toSeq

    val dependencies = modules.flatMap(_.getDependenciesList.getDependencies.asScala)

    val allModuleDependencies = dependencies.collect {
      case it: JpsModuleDependency if it.getModule != null => it.getModule
    }

    val transitiveDependenciesEnabled = areTransitiveDependenciesEnabled
     allModuleDependencies.collect {
      case it: JpsModule if it.getModuleType == SharedSourcesModuleType.INSTANCE &&
        (!transitiveDependenciesEnabled || !isSharedSourcesModulePresentInModulesDependencies(allModuleDependencies, it)) => it
    }
  }

  /**
   * When transitive dependencies feature is on, some module-X may have a shared sources module in its dependencies for two reasons: <br>
   * (1) it belongs to module-X (module-X is one of the owners of shared sources )<br>
   * (2) module-X depends on module-Y which has a shared sources <br>
   * When we are dealing with the second case, then module-X has in its dependencies module-Y and module-Y-shared. When checking whether module-Y-shared
   * should be added as a source dependency, we are taking all module-X dependencies (except from module-Y-shared) and checking whether any of these have in their dependencies
   * module-Y-shared. If yes, then it is a second case (module-X depends on module-Y and module-Y has in its dependencies module-Y-shared). In such case module-Y-shared
   * shouldn't be added as a source dependency to module-X.
   *
   * When we are dealing with the first case, the situation described above couldn't happen. Any of module-X dependencies must not contain in its dependencies module-X
   * (and thus module-X-shared), because this would imply a cyclical dependency which is not allowed in sbt. And precisely for this reason,
   * if there is no given shared sources module (module-X-shared) in the modules dependencies, then it is an information to us that it is shared sources module,
   * which must be added as a source dependency.
   *
   * @param dependentModules modules dependencies from the current <code>ModuleChunk</code>
   * @param targetSharedSourceModule shared sources module for which a check will be performed to see if it is a dependency of any module from the dependentModules
   * @return
   */
  private def isSharedSourcesModulePresentInModulesDependencies(dependentModules: Seq[JpsModule], targetSharedSourceModule: JpsModule): Boolean = {
    val modulesWithoutTargetShared = dependentModules.filterNot(_ == targetSharedSourceModule)
    modulesWithoutTargetShared.flatMap(_.getDependenciesList.getDependencies.asScala).exists {
      case it: JpsModuleDependency => it.getModule != null && it.getModule.getModuleType == SharedSourcesModuleType.INSTANCE && it.getModule == targetSharedSourceModule
      case _ => false
    }
  }

  private def areTransitiveDependenciesEnabled: Boolean = {
    // "sbt.process.dependencies.recursively" property is the negation of the SbtProjectSettings.insertProjectTransitiveDependencies value
    // (see ScalaBuildProcessParametersProvider.transitiveProjectDependenciesParams).
    // So the default value for "sbt.process.dependencies.recursively" is set to
    // the opposite of the default value of SbtProjectSettings.insertProjectTransitiveDependencies
    val shouldProcessDependenciesRecursively = Option(System.getProperty("sbt.process.dependencies.recursively")).flatMap(_.toBooleanOption).getOrElse(false)
    !shouldProcessDependenciesRecursively
  }
}
