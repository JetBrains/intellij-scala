package org.jetbrains.plugins.cbt.project.template

import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.cbt.CBT

class CbtProjectTemplate extends ProjectTemplate {
  override def getName = "CBT"

  override def getDescription = "CBT-based Scala project"

  override def getIcon = CBT.Icon

  override def createModuleBuilder() = new CbtModuleBuilder()

  override def validateSettings() = null
}
