package org.jetbrains.plugins.scala
package project.template

import com.intellij.icons.AllIcons
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.platform.ProjectTemplate

/**
 * @author Pavel Fatin
 */
class ScalaProjectTemplate extends ProjectTemplate {
  protected val libraryDescription: CustomLibraryDescription = ScalaLibraryDescription

  def languageName = "Scala"

  def getName = s"$languageName / IDEA project"

  def getDescription = s"Simple module with attached $languageName SDK"

  def getIcon = AllIcons.Nodes.Module

  def createModuleBuilder() = new ScalaModuleBuilder(languageName, libraryDescription)

  def validateSettings() = null
}
