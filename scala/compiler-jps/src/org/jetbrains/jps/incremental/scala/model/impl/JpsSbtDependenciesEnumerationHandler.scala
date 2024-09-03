package org.jetbrains.jps.incremental.scala.model.impl

import org.jetbrains.jps.incremental.scala.SettingsManager
import org.jetbrains.jps.incremental.scala.model.{JpsSbtExtensionService, JpsSbtModuleExtension}
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugins.scala.util.SbtModuleType

import java.util
import scala.jdk.CollectionConverters.IterableHasAsScala

/**
 * ATTENTION: implementation should be in sync with <br>
 * org.jetbrains.sbt.execution.SbtOrderEnumeratorHandler
 */
final class JpsSbtDependenciesEnumerationHandler(processDependenciesRecursively: Boolean) extends JpsJavaDependenciesEnumerationHandler {

  //TODO SCL-22835
  override def shouldAddRuntimeDependenciesToTestCompilationClasspath: Boolean =
    true

  override def shouldIncludeTestsFromDependentModulesToTestClasspath: Boolean =
    super.shouldIncludeTestsFromDependentModulesToTestClasspath

  override def shouldProcessDependenciesRecursively: Boolean =
    processDependenciesRecursively
}

object JpsSbtDependenciesEnumerationHandler {

  private val RecursiveDependenciesInstance = new JpsSbtDependenciesEnumerationHandler(true)
  private val NonRecursiveDependenciesInstance = new JpsSbtDependenciesEnumerationHandler(false)
  private val jpsSbtExtensionService = JpsSbtExtensionService.getInstance

  final class SbtFactory extends JpsJavaDependenciesEnumerationHandler.Factory {
    override def createHandler(modules: util.Collection[JpsModule]): JpsJavaDependenciesEnumerationHandler = {
      val moduleToExtension = getModuleToExtension(modules)

      val isNotSbtProject = moduleToExtension.isEmpty
      if (isNotSbtProject) return null
      // note: if separate modules for prod/test are enabled and the module isn't of sbtSourceSet type,
      // then dependencies should be processed recursively. In all other cases, dependencies shouldn't be processed recursively.
      val recursiveRequired = moduleToExtension.exists { case (module, ext) =>
        // TODO consider moving #isBuiltWithSeparateProdTestSources outside of the loop when #SCL-22991 is done
        isBuiltWithSeparateProdTestSources(module) && !isSbtSourceSetModule(ext)
      }

      if (recursiveRequired) RecursiveDependenciesInstance
      else NonRecursiveDependenciesInstance
    }

    private def getModuleToExtension(modules: util.Collection[JpsModule]): Map[JpsModule, JpsSbtModuleExtension] =
      modules.asScala.toSeq
        .map(jpsModule => jpsModule -> jpsSbtExtensionService.getExtension(jpsModule))
        .collect { case (jpsModule, Some(ext)) => (jpsModule, ext) }
        .toMap

    private def isSbtSourceSetModule(extension: JpsSbtModuleExtension): Boolean =
      extension.getModuleType.exists(_.equals(SbtModuleType.sbtSourceSetModuleType))

    private def isBuiltWithSeparateProdTestSources(module: JpsModule): Boolean = {
      val project = module.getProject
      val projectSettings = SettingsManager.getProjectSettings(project)
      projectSettings.getSeparateProdTestSources
    }
  }
}
