package org.jetbrains.plugins.scala
package projectView

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Pavel Fatin
 */
private[projectView] abstract class CustomDefinitionNode(definition: ScTypeDefinition)
                                                        (implicit project: Project, settings: ViewSettings)
  extends ClassTreeNode(project, definition, settings) with IconableNode {

  myName = definition.name

  override def updateImpl(data: PresentationData): Unit = validValue match {
    case Some(definition) => data.setPresentableText(definition.name)
    case _ => super.updateImpl(data)
  }

  protected final def validValue: Option[ScTypeDefinition] = getValue match {
    case definition: ScTypeDefinition if definition.isValid => Some(definition)
    case _ => None
  }
}
