package org.jetbrains.idea.devkit.scala.project

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.AbstractModuleBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.idea.devkit.scala.DevkitBundle

import javax.swing.Icon

class SbtIdeaPluginProjectTemplate extends ProjectTemplate {
  override def getName: String = DevkitBundle.message("sbtidea.template.name")
  override def getDescription: String = DevkitBundle.message("sbtidea.template.description")
  override def getIcon: Icon = AllIcons.Nodes.Plugin
  override def validateSettings(): ValidationInfo = null

  override def createModuleBuilder(): AbstractModuleBuilder = {
    val archiveUrl = getClass.getClassLoader.getResource("sbt-idea-example.zip")
    new SbtIdeaPluginProjectBuilder(archiveUrl)
  }
}
