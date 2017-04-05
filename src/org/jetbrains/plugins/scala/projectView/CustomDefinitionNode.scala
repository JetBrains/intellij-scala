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
private class CustomDefinitionNode(definition: ScTypeDefinition, icon: Icon)
                                  (implicit project: Project, settigns: ViewSettings)
  extends ClassTreeNode(project, definition, settigns) {

  myName = definition.name

  override def isAlwaysLeaf: Boolean = true

  override protected def getChildrenImpl: util.Collection[Node] = Collections.emptyList()

  override protected def updateImpl(data: PresentationData): Unit = {
    getValue match {
      case definition: ScTypeDefinition if definition.isValid => data.setPresentableText(definition.name)
      case _ => super.updateImpl(data)
    }

    data.setIcon(icon)
  }
}
