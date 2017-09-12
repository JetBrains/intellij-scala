package org.jetbrains.plugins.cbt.project

import javax.swing.Icon

import com.intellij.openapi.module.ModuleType
import org.jetbrains.plugins.cbt.project.template.CbtModuleBuilder
import org.jetbrains.plugins.scala.icons.Icons

class CbtExtraModuleType extends ModuleType[CbtModuleBuilder](CbtExtraModuleType.ID) {
  override def createModuleBuilder = new CbtModuleBuilder

  override def getName: String = CbtExtraModuleType.NAME

  override def getDescription: String = CbtExtraModuleType.NAME

  override def getNodeIcon(isOpened: Boolean): Icon = Icons.CBT_EXTRA_MODULE
}

object CbtExtraModuleType {
  val ID: String = "CBT_EXTRA_MODULE"
  val NAME: String = "CBT Extra Module"
}

