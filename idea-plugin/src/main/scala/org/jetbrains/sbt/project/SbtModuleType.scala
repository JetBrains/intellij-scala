package org.jetbrains.sbt
package project

import com.intellij.openapi.module.JavaModuleType
import com.intellij.icons.AllIcons

/**
 * @author Pavel Fatin
 */
class SbtModuleType extends JavaModuleType("SBT_MODULE") {
  override def getNodeIcon(isOpened: Boolean) = AllIcons.Actions.Compile
}

object SbtModuleType {
  val instance = Class.forName("org.jetbrains.sbt.project.SbtModuleType").newInstance.asInstanceOf[SbtModuleType]
}
