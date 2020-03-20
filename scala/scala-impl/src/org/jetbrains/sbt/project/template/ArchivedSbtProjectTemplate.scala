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

  override def getName: String = name

  override def getDescription: String = description

  override def getIcon: Icon = icon

  override def createModuleBuilder(): AbstractModuleBuilder = new ArchivedSbtProjectBuilder(this)

  override def validateSettings(): ValidationInfo = null
}