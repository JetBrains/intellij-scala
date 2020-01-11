package org.jetbrains.sbt

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import javax.swing.Icon

class SbtIconProvider extends ExternalSystemIconProvider {
  // TODO create sbt reload icon similar to icons.GradleIcons.GradleLoadChanges, SCL-16759
  override def getReloadIcon: Icon = com.intellij.icons.AllIcons.Actions.Refresh
}
