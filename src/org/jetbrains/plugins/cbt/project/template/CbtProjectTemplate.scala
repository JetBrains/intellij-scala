package org.jetbrains.plugins.cbt.project.template

import javax.swing.Icon

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.cbt.CBT

class CbtProjectTemplate extends ProjectTemplate {
  override def getName: String = "CBT"

  override def getDescription: String = "CBT-based Scala project"

  override def getIcon: Icon = CBT.Icon

  override def createModuleBuilder(): CbtModuleBuilder = new CbtModuleBuilder()

  override def validateSettings(): ValidationInfo = null
}
