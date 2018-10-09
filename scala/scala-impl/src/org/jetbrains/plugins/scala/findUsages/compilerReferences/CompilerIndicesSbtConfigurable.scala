package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class CompilerIndicesSbtConfigurable extends Configurable {
  private[this] val panel = new CompilerIndicesSbtSettingsForm

  override def getDisplayName: String        = "sbt compilation listener"
  override def createComponent(): JComponent = panel.mainPanel
  override def isModified: Boolean           = panel.isModified(CompilerIndicesSbtSettings())
  override def apply(): Unit                 = panel.applyTo(CompilerIndicesSbtSettings())
  override def reset(): Unit                 = panel.from(CompilerIndicesSbtSettings())
}
