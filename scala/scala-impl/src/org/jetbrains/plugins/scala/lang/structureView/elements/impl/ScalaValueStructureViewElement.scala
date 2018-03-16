package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

/**
* @author Alexander Podkhalyuzin
* Date: 08.05.2008
*/

class ScalaValueStructureViewElement private (element: ScNamedElement, val isInherited: Boolean, val showType: Boolean)
  extends ScalaStructureViewElement(element, isInherited) with TypedViewElement {

  override def getPresentation: ItemPresentation =
    new ScalaValueItemPresentation(element, isInherited, showType)

  override def getChildren: Array[TreeElement] = value match {
    case Some(definition: ScPatternDefinition) => definition.expr match {
      case Some(block: ScBlockExpr) => new ScalaBlockStructureViewElement(block).getChildren
      case _ => Array.empty
    }
    case _ => Array.empty
  }

  private def value = element.parentsInFile.findByType[ScValue]
}

object ScalaValueStructureViewElement {
  def apply(element: ScNamedElement, inherited: Boolean): Seq[ScalaValueStructureViewElement] =
    Seq(new ScalaValueStructureViewElement(element, inherited, showType = true),
      new ScalaValueStructureViewElement(element, inherited, showType = false))
}