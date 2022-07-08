package org.jetbrains.plugins.scala
package project.template

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.template.WizardEntity.{Module, Project}

import javax.swing.Icon

class ScalaProjectTemplate(entity: WizardEntity) extends ProjectTemplate {
  //noinspection ScalaExtractStringToBundle
  override def getName: String = entity match {
    case Project => "IDEA"
    case Module => "Scala"
  }

  override def getDescription: String = entity match {
    case Project => ScalaBundle.message("idea.based.scala.project")
    case Module => ScalaBundle.message("module.with.a.scala.sdk")
  }

  override def getIcon: Icon = entity match {
    case Project => AllIcons.Nodes.IdeaProject
    case Module => AllIcons.Nodes.Module
  }

  override def createModuleBuilder() = new ScalaModuleBuilder()

  override def validateSettings(): ValidationInfo = null
}
