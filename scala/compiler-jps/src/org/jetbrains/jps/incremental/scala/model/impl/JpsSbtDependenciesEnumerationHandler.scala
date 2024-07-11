package org.jetbrains.jps.incremental.scala.model.impl

import org.jetbrains.jps.incremental.scala.SettingsManager
import org.jetbrains.jps.incremental.scala.model.JpsSbtExtensionService
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.plugins.scala.util.SbtModuleType.sbtSourceSetModuleType

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

  final class SbtFactory extends JpsJavaDependenciesEnumerationHandler.Factory {
    override def createHandler(modules: util.Collection[JpsModule]): JpsJavaDependenciesEnumerationHandler = {
      // note: if separate modules for prod/test are enabled and the module isn't of sbtSourceSet type,
      // then dependencies should be processed recursively. In all other cases, dependencies shouldn't be processed recursively.
      val recursiveRequired = modules.asScala.exists { module =>
        isBuiltWithSeparateProdTestSources(module) && !isSbtSourceSetModule(module)
      }

      if (recursiveRequired) RecursiveDependenciesInstance
      else NonRecursiveDependenciesInstance
    }

    private def isSbtSourceSetModule(module: JpsModule): Boolean = {
      val service = JpsSbtExtensionService.getInstance
      val sbtModuleExtension = service.getExtension(module)
      sbtModuleExtension match {
        case Some(ext) =>
          ext.getModuleType.exists(_.equals(sbtSourceSetModuleType))
        // note: if for any reason sbtModuleExtension is None, then true is returned.
        // It's done this way, because if true is returned, the dependency processing will be non-recursive, and it's the default behavior
        case _ => true
      }
    }

    private def isBuiltWithSeparateProdTestSources(module: JpsModule): Boolean = {
      val project = module.getProject
      val projectSettings = SettingsManager.getProjectSettings(project)
      projectSettings.getSeparateProdTestSources
    }
  }
}
