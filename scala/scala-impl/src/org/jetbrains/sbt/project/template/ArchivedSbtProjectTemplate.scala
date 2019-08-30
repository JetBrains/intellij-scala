package org.jetbrains.sbt.project.template

import java.net.URL

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.ProjectTemplate
import javax.swing.Icon
import org.jetbrains.sbt.Sbt

case class ArchivedSbtProjectTemplate(name: String,
                                      description: String,
                                      url: URL,
                                      icon: Icon = Sbt.Icon) extends ProjectTemplate {

  def getName: String = name

  def getDescription: String = description

  def getIcon: Icon = icon

  def createModuleBuilder(): AbstractModuleBuilder = new ArchivedSbtProjectBuilder(this)

  def validateSettings(): ValidationInfo = null
}