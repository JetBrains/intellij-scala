package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import javax.swing.Icon

class BspIconProvider extends ExternalSystemIconProvider {
  // TODO create sbt reload icon similar to icons.GradleIcons.GradleLoadChanges, SCL-16857
  override def getReloadIcon: Icon = com.intellij.icons.AllIcons.Actions.Refresh
}
