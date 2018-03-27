package org.jetbrains.plugins.scala.lang.structureView.element

import javax.swing.Icon

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.util.PlatformIcons
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

private class Block(block: ScBlock) extends Element(block) {
  override def getPresentableText: String = ""

  override def getIcon(open: Boolean): Icon = PlatformIcons.CLASS_INITIALIZER

  override def getChildren: Array[TreeElement] = {
    val result = block.getChildren.flatMap {
      case element @ ((_: ScFunction) | (_: ScTypeDefinition) | (_: ScBlockExpr)) => Element(element)
      case _ => Seq.empty
     }

    result.toArray
  }
}
