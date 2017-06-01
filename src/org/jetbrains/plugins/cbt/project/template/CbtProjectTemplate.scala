package org.jetbrains.plugins.cbt.project.template

import com.intellij.platform.ProjectTemplate
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.template.SbtModuleBuilder

class CbtProjectTemplate extends ProjectTemplate {
  override def getName = "CBT"

  override def getDescription = "CBT-based Scala project"

  override def getIcon = Sbt.Icon

  override def createModuleBuilder() = new SbtModuleBuilder()

  override def validateSettings() = null
}
