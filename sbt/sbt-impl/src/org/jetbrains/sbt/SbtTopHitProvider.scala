package org.jetbrains.sbt

import com.intellij.ide.ActionsTopHitProvider

class SbtTopHitProvider extends ActionsTopHitProvider {
  override def getActionsMatrix: Array[Array[String]] = Array(
      Array("s", "sbt", "ExternalSystem.RefreshAllProjects"),
      Array("reload all s", "reload all sbt Projects", "ExternalSystem.RefreshAllProjects"),
      Array("load s", "load sbt changes", "ExternalSystem.ProjectRefreshAction")
    )
}
