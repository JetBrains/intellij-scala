package org.jetbrains.sbt
package project.template

import javax.swing.Icon

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate

/**
 * User: Dmitry.Naydanov, Pavel Fatin
 * Date: 11.03.14.
 */
class SbtProjectTemplate extends ProjectTemplate {
  override def getName = "SBT"

  override def getDescription = "SBT-based Scala project (recommended)"

  override def getIcon: Icon = Sbt.Icon

  override def createModuleBuilder() = new SbtModuleBuilder()

  override def validateSettings(): ValidationInfo = null
}
