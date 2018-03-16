package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableStructureViewElement private (element: ScNamedElement, inherited: Boolean, val showType: Boolean)
  extends ScalaStructureViewElement(element, inherited) with TypedViewElement {

  override def getPresentation: ItemPresentation =
    new ScalaVariableItemPresentation(element, inherited, showType)

  override def getChildren: Array[TreeElement] = variable match {
    case Some(definition: ScVariableDefinition) => definition.expr match {
      case Some(block: ScBlockExpr) => new ScalaBlockStructureViewElement(block).getChildren
      case _ => Array.empty
    }
    case _ => Array.empty
  }

  private def variable = element.parentsInFile.findByType[ScVariable]
}

object ScalaVariableStructureViewElement {
  def apply(element: ScNamedElement, inherited: Boolean): Seq[ScalaVariableStructureViewElement] =
    Seq(//new ScalaVariableStructureViewElement(element, inherited, showType = true),
      new ScalaVariableStructureViewElement(element, inherited, showType = false))
}