package org.jetbrains.sbt
package project.module

import com.intellij.openapi.module.{Module, ModuleType}
import org.jetbrains.annotations.NonNls
import org.jetbrains.sbt.project.DummyModuleBuilder
import org.jetbrains.sbt.project.module.SbtModuleType.Id

import javax.swing.Icon

class SbtModuleType extends ModuleType[DummyModuleBuilder](Id) {
  override def createModuleBuilder: DummyModuleBuilder = new DummyModuleBuilder()

  override def getName: String = Sbt.BuildModuleName

  override def getDescription: String = Sbt.BuildModuleDescription

  override def getNodeIcon(isOpened: Boolean): Icon = Sbt.FolderIcon
}

object SbtModuleType {

  @NonNls val Id = "SBT_MODULE"

  val instance: SbtModuleType =
    Class.forName("org.jetbrains.sbt.project.module.SbtModuleType")
      .getConstructor().newInstance().asInstanceOf[SbtModuleType]

  def unapply(m: Module): Option[Module] =
    if (ModuleType.get(m).isInstanceOf[SbtModuleType]) Some(m)
    else None
}
