package org.jetbrains.bsp

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import javax.swing.Icon

object bsp {
  val Name = "bsp"
  val Icon: Icon = AllIcons.Nodes.IdeaProject // TODO bsp icon

  val ProjectSystemId = new ProjectSystemId("BSP", Name)
}
