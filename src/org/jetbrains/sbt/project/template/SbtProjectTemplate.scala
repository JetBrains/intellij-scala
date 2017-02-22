package org.jetbrains.sbt
package project.template

import com.intellij.platform.ProjectTemplate
import org.jetbrains.plugins.scala.icons.Icons

/**
 * User: Dmitry.Naydanov, Pavel Fatin
 * Date: 11.03.14.
 */
class SbtProjectTemplate extends ProjectTemplate {
  override def getName = "Scala"

  override def getDescription = "SBT-based Scala project"

  override def getIcon = Icons.SCALA_SMALL_LOGO

  override def createModuleBuilder() = new SbtModuleBuilder()

  override def validateSettings() = null
}
