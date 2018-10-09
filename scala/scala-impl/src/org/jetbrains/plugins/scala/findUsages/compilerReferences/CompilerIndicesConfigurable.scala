package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class CompilerIndicesConfigurable(project: Project) extends Configurable {
  private[this] val panel = new CompilerIndicesSettingsForm(project)

  override def getDisplayName: String        = "Compiler Indices"
  override def createComponent(): JComponent = panel.mainPanel
  override def isModified: Boolean           = panel.isModified(CompilerIndicesSettings(project))
  override def apply(): Unit                 = panel.applyTo(CompilerIndicesSettings(project))
  override def reset(): Unit                 = panel.from(CompilerIndicesSettings(project))
}
