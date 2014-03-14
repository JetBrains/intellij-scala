package org.jetbrains.sbt
package project.module

import com.intellij.openapi.module.ModuleType
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder

/**
 * @author Pavel Fatin
 */
class SbtModuleType extends ModuleType[EmptyModuleBuilder]("SBT_MODULE") {
  def createModuleBuilder() = new EmptyModuleBuilder()

  def getName = Sbt.BuildModuleName

  def getDescription = Sbt.BuildModuleDescription

  def getBigIcon = Sbt.Icon

  override def getNodeIcon(isOpened: Boolean) = Sbt.Icon
}

object SbtModuleType {
  val instance = Class.forName("org.jetbrains.sbt.project.module.SbtModuleType").newInstance.asInstanceOf[SbtModuleType]
}
