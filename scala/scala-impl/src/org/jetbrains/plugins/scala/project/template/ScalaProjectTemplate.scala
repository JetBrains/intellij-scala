package org.jetbrains.plugins.scala
package project.template

import javax.swing.Icon
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.project.template.WizardEntity.{Module, Project}

/**
  * @author Pavel Fatin
  */
class ScalaProjectTemplate(entity: WizardEntity) extends ProjectTemplate {
  override def getName: String = entity match {
    case Project => "IDEA"
    case Module => "Scala"
  }

  override def getDescription: String = entity match {
    case Project => "IDEA-based Scala project"
    case Module => "Module with a Scala SDK"
  }

  override def getIcon: Icon = entity match {
    case Project => AllIcons.Nodes.IdeaProject
    case Module => AllIcons.Nodes.Module
  }

  override def createModuleBuilder() = new ScalaModuleBuilder()

  override def validateSettings(): ValidationInfo = null
}
