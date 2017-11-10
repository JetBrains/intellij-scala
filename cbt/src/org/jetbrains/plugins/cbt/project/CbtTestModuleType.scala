package org.jetbrains.plugins.cbt.project

import javax.swing.Icon

import com.intellij.openapi.module.JavaModuleType
import org.jetbrains.plugins.scala.icons.Icons

class CbtTestModuleType extends JavaModuleType(CbtTestModuleType.ID) {
  override def getName: String = CbtTestModuleType.NAME

  override def getDescription: String = CbtTestModuleType.NAME

  override def getNodeIcon(isOpened: Boolean): Icon = Icons.CBT_TEST_MODULE
}

object CbtTestModuleType {
  val ID: String = "CBT_TEST_MODULE"
  val NAME: String = "CBT Test Module"
}

