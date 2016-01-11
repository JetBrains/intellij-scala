package org.jetbrains.plugins.dotty.project.template

import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import org.jetbrains.plugins.scala.project.template.ScalaProjectTemplate

/**
  * @author adkozlov
  */
class DottyProjectTemplate extends ScalaProjectTemplate {
  override protected val libraryDescription: CustomLibraryDescription = DottyLibraryDescription

  override def getName = "Dotty"
}
