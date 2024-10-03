package org.jetbrains.jps.incremental.scala.model.impl

import org.jetbrains.jps.incremental.scala.model.JpsSbtModuleExtension
import org.jetbrains.jps.model.ex.{JpsElementBase, JpsElementChildRoleBase}
import org.jetbrains.jps.model.{JpsElementChildRole, JpsSimpleElement}

class JpsSbtModuleExtensionImpl(moduleType: Option[String], displayModuleName: Option[String]) extends JpsElementBase[JpsSbtModuleExtensionImpl] with JpsSbtModuleExtension {

  override def getModuleType: Option[String] = moduleType
  override def getDisplayModuleName: Option[String] = displayModuleName
}

object JpsSbtModuleExtensionImpl {
  val Role: JpsElementChildRole[JpsSbtModuleExtension] = JpsElementChildRoleBase.create("sbt")
  val ProductionOnTestRole: JpsElementChildRole[JpsSimpleElement[Boolean]] = JpsElementChildRoleBase.create("sbt production on test")
}
