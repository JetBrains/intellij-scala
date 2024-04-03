package org.jetbrains.jps.incremental.scala.model.impl

import org.jetbrains.jps.incremental.scala.model.JpsSbtExtensionService
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler
import org.jetbrains.jps.model.module.JpsModule

import java.util
import scala.jdk.CollectionConverters.IterableHasAsScala

/**
 * ATTENTION: implementation should be in sync with<br>
 * org.jetbrains.sbt.execution.SbtOrderEnumeratorHandler
 */
final class JpsSbtDependenciesEnumerationHandler extends JpsJavaDependenciesEnumerationHandler {

  override def shouldAddRuntimeDependenciesToTestCompilationClasspath: Boolean =
    true

  override def shouldIncludeTestsFromDependentModulesToTestClasspath: Boolean =
    super.shouldIncludeTestsFromDependentModulesToTestClasspath

  override def shouldProcessDependenciesRecursively: Boolean = {
    // "sbt.process.dependencies.recursively" property is the negation of the SbtProjectSettings.insertProjectTransitiveDependencies value
    // (see ScalaBuildProcessParametersProvider.transitiveProjectDependenciesParams).
    // So the default value for "sbt.process.dependencies.recursively" is set to
    // the opposite of the default value of SbtProjectSettings.insertProjectTransitiveDependencies
    Option(System.getProperty("sbt.process.dependencies.recursively")).flatMap(_.toBooleanOption)
      .getOrElse(false)
  }
}

object JpsSbtDependenciesEnumerationHandler {
  private val Instance = new JpsSbtDependenciesEnumerationHandler

  final class SbtFactory extends JpsJavaDependenciesEnumerationHandler.Factory {
    override def createHandler(modules: util.Collection[JpsModule]): JpsJavaDependenciesEnumerationHandler = {
      val service = JpsSbtExtensionService.getInstance
      val extension = modules.asScala.iterator.flatMap(service.getExtension).nextOption()
      extension.map(_ => Instance).orNull
    }
  }
}
