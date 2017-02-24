package org.jetbrains.plugins.dotty.project.template

import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import org.jetbrains.plugins.scala.project.template.WizardEntity._
import org.jetbrains.plugins.scala.project.template.{ScalaModuleBuilder, ScalaProjectTemplate, WizardEntity}

/**
  * @author adkozlov
  */
// TODO merge with the Scala template
class DottyProjectTemplate(entity: WizardEntity) extends ScalaProjectTemplate(entity) {
  override protected val libraryDescription: CustomLibraryDescription = DottyLibraryDescription

  override def getName: String = entity match {
    case Project => "IDEA (Dotty)"
    case Module => "Dotty"
  }

  override def getDescription: String = entity match {
    case Project => "IDEA-based Dotty project"
    case Module => "Module with a Dotty SDK"
  }

  override def createModuleBuilder() = new ScalaModuleBuilder("Dotty", libraryDescription)
}
