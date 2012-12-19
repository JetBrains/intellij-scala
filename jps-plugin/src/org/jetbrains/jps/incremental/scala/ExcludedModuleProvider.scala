package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.service.JpsServiceManager
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
abstract class ExcludedModuleProvider {
  def isExcluded(module: JpsModule): Boolean
}

object ExcludedModuleProvider {
  def isExcluded(module: JpsModule): Boolean = {
    val providers = JpsServiceManager.getInstance.getExtensions(classOf[ExcludedModuleProvider]).asScala
    providers.exists(_.isExcluded(module))
  }
}
