package org.jetbrains.bsp

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.util.Icons

object bsp {
  val Name = "bsp"
  val Icon = AllIcons.Nodes.IdeaProject

  val ProjectSystemId = new ProjectSystemId("BSP", Name)
}
