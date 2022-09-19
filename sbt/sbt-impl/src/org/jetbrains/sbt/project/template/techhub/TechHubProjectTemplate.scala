package org.jetbrains.sbt.project.template.techhub

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.SbtBundle

import javax.swing.Icon

class TechHubProjectTemplate extends ProjectTemplate {
  override def getName: String = SbtBundle.message("sbt.techhub.lightbend.project.starter")

  override def getDescription: String = SbtBundle.message("sbt.techhub.sbt.based.project.from.a.lightbend.tech.hub.template")

  override def getIcon: Icon = Icons.LIGHTBEND_LOGO

  override def validateSettings(): ValidationInfo = null

  override def createModuleBuilder(): AbstractModuleBuilder = new TechHubModuleBuilder
}
