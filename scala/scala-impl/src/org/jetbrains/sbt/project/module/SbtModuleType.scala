package org.jetbrains.sbt
package project.module

import javax.swing.Icon

import com.intellij.ide.util.projectWizard.EmptyModuleBuilder
import com.intellij.openapi.module.{Module, ModuleType}
import SbtModuleType.Id

/**
 * @author Pavel Fatin
 */
class SbtModuleType extends ModuleType[EmptyModuleBuilder](Id) {
  def createModuleBuilder() = new EmptyModuleBuilder()

  override def getName: String = Sbt.BuildModuleName

  override def getDescription: String = Sbt.BuildModuleDescription

  override def getNodeIcon(isOpened: Boolean): Icon = Sbt.FolderIcon
}

object SbtModuleType {

  val Id = "SBT_MODULE"

  val instance: SbtModuleType =
    Class.forName("org.jetbrains.sbt.project.module.SbtModuleType").newInstance.asInstanceOf[SbtModuleType]

  def unapply(m: Module): Option[Module] =
    if (ModuleType.get(m).isInstanceOf[SbtModuleType]) Some(m)
    else None
}
