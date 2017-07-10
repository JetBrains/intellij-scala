package org.jetbrains.plugins.cbt.project

import com.intellij.openapi.module.ModuleType
import org.jetbrains.plugins.cbt.project.template.CbtModuleBuilder
import org.jetbrains.plugins.scala.icons.Icons

class CbtExtraModuleType extends ModuleType[CbtModuleBuilder](CbtExtraModuleType.ID) {
  override def createModuleBuilder = new CbtModuleBuilder

  override def getName = CbtExtraModuleType.NAME

  override def getDescription = CbtExtraModuleType.NAME

  override def getNodeIcon(isOpened: Boolean) = Icons.ADD_CLAUSE
}

object CbtExtraModuleType {
  val ID: String = "CBT_EXTRA_MODULE"
  val NAME: String = "CBT extra Module"
}

