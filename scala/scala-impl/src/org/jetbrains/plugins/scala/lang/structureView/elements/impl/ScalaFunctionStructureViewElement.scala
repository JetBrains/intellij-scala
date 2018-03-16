package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFunctionStructureViewElement private (function: ScFunction, val isInherited: Boolean, val showType: Boolean)
  extends ScalaStructureViewElement(function, isInherited) with TypedViewElement {

  def getPresentation: ItemPresentation =
    new ScalaFunctionItemPresentation(function, isInherited, showType)

  override def getChildren: Array[TreeElement] = function match {
    case definition: ScFunctionDefinition => definition.body match {
      case Some(block: ScBlockExpr) => new ScalaBlockStructureViewElement(block).getChildren
      case _ => Array.empty
    }
    case _ => Array.empty
  }
}

object ScalaFunctionStructureViewElement {
  def apply(function: ScFunction, isInherited: Boolean): Seq[ScalaFunctionStructureViewElement] =
    Seq(//new ScalaFunctionStructureViewElement(function, isInherited, showType = true),
      new ScalaFunctionStructureViewElement(function, isInherited, showType = false))
}