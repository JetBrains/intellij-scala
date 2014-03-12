package org.jetbrains.sbt
package project.module

import com.intellij.platform.ProjectTemplate
import javax.swing.Icon
import com.intellij.ide.util.projectWizard.AbstractModuleBuilder
import com.intellij.openapi.ui.ValidationInfo

/**
 * User: Dmitry.Naydanov
 * Date: 11.03.14.
 */
class SbtProjectTemplate extends ProjectTemplate {
  override def validateSettings(): ValidationInfo = null

  override def createModuleBuilder(): AbstractModuleBuilder = new SbtModuleBuilder

  override def getIcon: Icon = Sbt.Icon

  override def getDescription: String = "Project backed by SBT"

  override def getName: String = "SBT Project"
}
