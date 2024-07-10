package org.jetbrains.jps.incremental.scala.model

import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.service.JpsServiceManager

trait JpsSbtExtensionService {
  def getExtension(module: JpsModule): Option[JpsSbtModuleExtension]
  def getOrCreateExtension(module: JpsModule, moduleType: Option[String]): JpsSbtModuleExtension
}

object JpsSbtExtensionService {
  def getInstance: JpsSbtExtensionService =
    JpsServiceManager.getInstance.getService(classOf[JpsSbtExtensionService]);
}
