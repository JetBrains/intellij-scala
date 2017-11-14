package org.jetbrains.plugins.cbt.project

import javax.swing.Icon

import com.intellij.openapi.module.JavaModuleType
import org.jetbrains.plugins.scala.icons.Icons

class CbtExtraModuleType extends JavaModuleType(CbtExtraModuleType.ID) {
  override def getName: String = CbtExtraModuleType.NAME

  override def getDescription: String = CbtExtraModuleType.NAME

  override def getNodeIcon(isOpened: Boolean): Icon = Icons.CBT_EXTRA_MODULE
}

object CbtExtraModuleType {
  val ID: String = "CBT_EXTRA_MODULE"
  val NAME: String = "CBT Extra Module"
}

