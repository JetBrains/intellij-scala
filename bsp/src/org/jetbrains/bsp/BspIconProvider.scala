package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import javax.swing.Icon

class BspIconProvider extends ExternalSystemIconProvider {
  override def getReloadIcon: Icon = Icons.BSP_LOAD_CHANGES
}
