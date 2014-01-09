package org.jetbrains.sbt
package project.module

import com.intellij.openapi.module.ModuleType
import org.jetbrains.plugins.scala.config.ScalaFacetAvailabilityMarker

/**
 * @author Pavel Fatin
 */
class SbtModuleType extends ModuleType[SbtModuleBuilder]("SBT_MODULE") with ScalaFacetAvailabilityMarker {
  override def getNodeIcon(isOpened: Boolean) = Sbt.Icon

  def createModuleBuilder() = new SbtModuleBuilder()

  def getName = Sbt.BuildModuleName

  def getDescription = Sbt.BuildModuleDescription

  def getBigIcon = Sbt.Icon
}

object SbtModuleType {
  val instance = Class.forName("org.jetbrains.sbt.project.module.SbtModuleType").newInstance.asInstanceOf[SbtModuleType]
}
