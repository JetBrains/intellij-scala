package org.jetbrains.jps.incremental.scala.sources

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.scala.SourceDependenciesProviderService
import org.jetbrains.jps.model.module.{JpsModule, JpsModuleDependency}

import scala.jdk.CollectionConverters._

class SharedSourceDependenciesProviderService extends SourceDependenciesProviderService {
  override def getSourceDependenciesFor(chunk: ModuleChunk): Seq[JpsModule] = {
    val modules = chunk.getModules.asScala.toSeq

    val dependencies = modules.flatMap(_.getDependenciesList.getDependencies.asScala)

    // should be in sync with org.jetbrains.sbt.project.settings.SbtProjectSettings.separateProdAndTestSources
    val prodTestSourcesSeparated = Option(System.getProperty("sbt.prod.test.separated")).flatMap(_.toBooleanOption).getOrElse(false)

    val representativeTargetName = chunk.representativeTarget().getModule.getName.takeRight(4)
    val isSuitableName: JpsModule => Boolean = { jpsModule =>
      // note: when modules are separated to main/test in projects that contain shared sources its test module will have dependencies
      // to shared sources test module and shared sources main module. Because test module has also a dependency to shared sources main module
      // we cannot treat it as a source dependency for this module.
      // To decide what shared sources module should be taken into account a simple suffix comparison was made - e.g. modules with suffix "test" should add shared sources
      // modules with the same suffix ("test)
      !prodTestSourcesSeparated || jpsModule.getName.takeRight(4) == representativeTargetName
    }

    dependencies.collect {
      case it: JpsModuleDependency if {
        val module = it.getModule
        module != null && module.getModuleType == SharedSourcesModuleType.INSTANCE && isSuitableName(module)
      } => it.getModule
    }
  }
}
