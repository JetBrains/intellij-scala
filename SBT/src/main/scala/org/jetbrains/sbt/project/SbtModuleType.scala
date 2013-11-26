package org.jetbrains.sbt
package project

import com.intellij.openapi.module.{ModuleType, JavaModuleType}
import org.jetbrains.plugins.scala.config.ScalaFacetAvailabilityMarker
import javax.swing.Icon

/**
 * @author Pavel Fatin
 */
class SbtModuleType extends ModuleType[SbtModuleBuilder]("SBT_MODULE") with ScalaFacetAvailabilityMarker {
  override def getNodeIcon(isOpened: Boolean) = Sbt.Icon

  def createModuleBuilder(): SbtModuleBuilder = new SbtModuleBuilder

  def getName: String = "SBT module"

  def getDescription: String = "Create empty SBT project"

  def getBigIcon: Icon = Sbt.Icon
}

object SbtModuleType {
  val instance = Class.forName("org.jetbrains.sbt.project.SbtModuleType").newInstance.asInstanceOf[SbtModuleType]
}
