package org.jetbrains.jps.incremental.scala.model.impl

import org.jetbrains.jps.incremental.scala.model.JpsSbtModuleExtension
import org.jetbrains.jps.model.ex.{JpsElementBase, JpsElementChildRoleBase}
import org.jetbrains.jps.model.JpsElementChildRole

final class JpsSbtModuleExtensionImpl extends JpsElementBase[JpsSbtModuleExtensionImpl] with JpsSbtModuleExtension {
  override def createCopy(): JpsSbtModuleExtensionImpl = new JpsSbtModuleExtensionImpl

  override def applyChanges(modified: JpsSbtModuleExtensionImpl): Unit = {}
}

object JpsSbtModuleExtensionImpl {
  val Role: JpsElementChildRole[JpsSbtModuleExtension] = JpsElementChildRoleBase.create("sbt")
}
