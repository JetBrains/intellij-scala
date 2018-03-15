package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

class ScalaBlockStructureViewElement(block: ScBlock) extends ScalaStructureViewElement(block, false) {
  override def getPresentation: ItemPresentation = new ScalaBlockItemPresentation(block)

  override def getChildren: Array[TreeElement] = {
    val result = block.getChildren.flatMap {
      case function: ScFunction => Seq(new ScalaFunctionStructureViewElement(function, false))
      case typeDefinition: ScTypeDefinition => Seq(new ScalaTypeDefinitionStructureViewElement(typeDefinition))
      case block: ScBlockExpr => Seq(new ScalaBlockStructureViewElement(block))
      case _ => Seq.empty
     }

    result.toArray
  }
}