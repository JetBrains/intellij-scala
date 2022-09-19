package org.jetbrains.sbt

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import org.jetbrains.sbt.icons.Icons

import javax.swing.Icon

class SbtIconProvider extends ExternalSystemIconProvider {
  // TODO create sbt reload icon similar to icons.GradleIcons.GradleLoadChanges, SCL-16759
  override def getReloadIcon: Icon = Icons.SBT_LOAD_CHANGES
}
