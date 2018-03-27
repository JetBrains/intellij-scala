package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import javax.swing.Icon

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.util.PlatformIcons
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

class ScalaBlockStructureViewElement(block: ScBlock) extends ScalaStructureViewElement(block) {
  override def getPresentableText: String = ""

  override def getIcon(open: Boolean): Icon = PlatformIcons.CLASS_INITIALIZER

  override def getChildren: Array[TreeElement] = {
    val result = block.getChildren.flatMap {
      case element @ ((_: ScFunction) | (_: ScTypeDefinition) | (_: ScBlockExpr)) => ScalaStructureViewElement(element)
      case _ => Seq.empty
     }

    result.toArray
  }
}
