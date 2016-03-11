package org.jetbrains.plugins.scala
package project.template

import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Pavel Fatin
 */
class ScalaProjectTemplate extends ProjectTemplate {
  protected val libraryDescription: CustomLibraryDescription = ScalaLibraryDescription

  def getName = "Scala"

  def getDescription = s"Simple module with attached $getName SDK"

  def getIcon = Icons.SCALA_SMALL_LOGO

  def createModuleBuilder() = new ScalaModuleBuilder(getName, libraryDescription)

  def validateSettings() = null
}
