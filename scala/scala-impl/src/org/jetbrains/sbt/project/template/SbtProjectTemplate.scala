package org.jetbrains.sbt
package project.template

import javax.swing.Icon
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import org.jetbrains.sbt.SbtBundle

/**
 * User: Dmitry.Naydanov, Pavel Fatin
 * Date: 11.03.14.
 */
class SbtProjectTemplate extends ProjectTemplate {
  //noinspection ReferencePassedToNls
  override def getName: String = Sbt.Name

  override def getDescription: String = SbtBundle.message("sbt.based.scala.project.recommended")

  override def getIcon: Icon = Sbt.Icon

  override def createModuleBuilder() = new SbtModuleBuilder()

  override def validateSettings(): ValidationInfo = null
}
