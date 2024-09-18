package org.jetbrains.bsp

import com.intellij.ide.ActionsTopHitProvider

class BspTopHitProvider extends ActionsTopHitProvider {
  override def getActionsMatrix: Array[Array[String]] = Array(
      Array("b", "bsp ", "ExternalSystem.RefreshAllProjects"),
      Array("reload all b", "reload all bsp Projects ", "ExternalSystem.RefreshAllProjects"),
      Array("load b", "load bsp changes ", "ExternalSystem.ProjectRefreshAction")
    )
}
