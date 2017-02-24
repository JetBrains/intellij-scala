package org.jetbrains.plugins.scala
package project.template

import javax.swing.Icon

import com.intellij.icons.AllIcons
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.project.template.WizardEntity.{Module, Project}

/**
  * @author Pavel Fatin
  */
class ScalaProjectTemplate(entity: WizardEntity) extends ProjectTemplate {
  def getName: String = entity match {
    case Project => "IDEA"
    case Module => "Scala"
  }

  def getDescription: String = entity match {
    case Project => "IDEA-based Scala project"
    case Module => "Module with a Scala SDK"
  }

  def getIcon: Icon = entity match {
    case Project => AllIcons.Nodes.IdeaProject
    case Module => AllIcons.Nodes.Module
  }

  def createModuleBuilder() = new ScalaModuleBuilder()

  def validateSettings() = null
}
