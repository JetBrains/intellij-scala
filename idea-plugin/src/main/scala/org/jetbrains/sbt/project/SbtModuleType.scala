package org.jetbrains.sbt
package project

import com.intellij.openapi.module.JavaModuleType

/**
 * @author Pavel Fatin
 */
class SbtModuleType extends JavaModuleType("SBT_MODULE") {
  override def getNodeIcon(isOpened: Boolean) = Sbt.Icon
}

object SbtModuleType {
  val instance = Class.forName("org.jetbrains.sbt.project.SbtModuleType").newInstance.asInstanceOf[SbtModuleType]
}
