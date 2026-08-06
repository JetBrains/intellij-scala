package org.jetbrains.sbt

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import org.jetbrains.sbt.icons.Icons

import javax.swing.Icon

class SbtIconProvider extends ExternalSystemIconProvider {
  override def getReloadIcon: Icon = Icons.SBT_LOAD_CHANGES
}
