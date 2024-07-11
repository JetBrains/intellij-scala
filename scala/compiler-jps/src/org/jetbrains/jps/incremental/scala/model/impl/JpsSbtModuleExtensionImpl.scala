package org.jetbrains.jps.incremental.scala.model.impl

import org.jetbrains.jps.incremental.scala.model.JpsSbtModuleExtension
import org.jetbrains.jps.model.ex.{JpsElementBase, JpsElementChildRoleBase}
import org.jetbrains.jps.model.JpsElementChildRole

class JpsSbtModuleExtensionImpl(moduleType: Option[String]) extends JpsElementBase[JpsSbtModuleExtensionImpl] with JpsSbtModuleExtension {

  override def getModuleType: Option[String] = moduleType
}

object JpsSbtModuleExtensionImpl {
  val Role: JpsElementChildRole[JpsSbtModuleExtension] = JpsElementChildRoleBase.create("sbt")
}
