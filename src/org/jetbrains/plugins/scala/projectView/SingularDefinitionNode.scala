package org.jetbrains.plugins.scala.projectView

import java.util
import java.util.Collections
import javax.swing.Icon

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
  * @author Pavel Fatin
  */
class SingularDefinitionNode(project: Project, definition: ScTypeDefinition, icon: Icon, settigns: ViewSettings)
  extends ClassTreeNode(project, definition, settigns) {

  override def isAlwaysLeaf: Boolean = true

  override def getChildrenImpl: util.Collection[Node] = Collections.emptyList()

  override def updateImpl(data: PresentationData): Unit = {
    data.setPresentableText(definition.name)
    data.setIcon(icon)
  }
}
