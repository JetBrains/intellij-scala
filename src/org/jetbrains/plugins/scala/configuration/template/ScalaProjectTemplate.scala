package org.jetbrains.plugins.scala
package configuration.template

import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Pavel Fatin
 */
class ScalaProjectTemplate extends ProjectTemplate {
  def getName = "IDEA project"

  def getDescription = "IDEA-based Scala project"

  def getIcon = Icons.SCALA_SMALL_LOGO

  def createModuleBuilder() = new ScalaModuleBuilder()

  def validateSettings() = null
}
