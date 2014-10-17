package org.jetbrains.plugins.scala
package project.template

import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Pavel Fatin
 */
class ScalaProjectTemplate extends ProjectTemplate {
  def getName = "Scala"

  def getDescription = "Simple module with attached Scala SDK"

  def getIcon = Icons.SCALA_SMALL_LOGO

  def createModuleBuilder() = new ScalaModuleBuilder()

  def validateSettings() = null
}
