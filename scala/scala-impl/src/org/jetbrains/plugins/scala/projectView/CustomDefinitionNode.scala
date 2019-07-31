package org.jetbrains.plugins.scala
package projectView

import java.{util => ju}

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Pavel Fatin
 */
private[projectView] abstract class CustomDefinitionNode(definition: ScTypeDefinition)
                                                        (implicit project: Project, settings: ViewSettings)
  extends ClassTreeNode(project, definition, settings) with IconProviderNode {

  myName = definition.name

  override def isAlwaysLeaf: Boolean = true

  override protected def getChildrenImpl: ju.Collection[Node] = ju.Collections.emptyList()

  override protected def updateImpl(data: PresentationData): Unit = {
    getValue match {
      case definition: ScTypeDefinition if definition.isValid => data.setPresentableText(definition.name)
      case _ => super.updateImpl(data)
    }

    setIcon(data)
  }
}
