package org.jetbrains.sbt

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons

class SbtIconProvider extends ExternalSystemIconProvider {
  // TODO create sbt reload icon similar to icons.GradleIcons.GradleLoadChanges, SCL-16759
  override def getReloadIcon: Icon = Icons.SBT_LOAD_CHANGES
}
