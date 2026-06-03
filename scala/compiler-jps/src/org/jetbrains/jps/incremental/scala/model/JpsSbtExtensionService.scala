package org.jetbrains.jps.incremental.scala.model

import org.jetbrains.jps.model.module.{JpsDependencyElement, JpsModule}
import org.jetbrains.jps.service.JpsServiceManager

trait JpsSbtExtensionService {
  def getExtension(module: JpsModule): Option[JpsSbtModuleExtension]
  def getOrCreateExtension(module: JpsModule, moduleType: Option[String], displayModuleName: Option[String]): JpsSbtModuleExtension
  def isProductionOnTestDependency(dependency: JpsDependencyElement): Boolean
  def setProductionOnTestDependency(dependency: JpsDependencyElement, value: Boolean): Unit
}

object JpsSbtExtensionService {
  def getInstance: JpsSbtExtensionService =
    JpsServiceManager.getInstance.getService(classOf[JpsSbtExtensionService]);
}
