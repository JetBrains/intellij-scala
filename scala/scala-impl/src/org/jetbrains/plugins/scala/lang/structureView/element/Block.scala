package org.jetbrains.plugins.scala.lang.structureView.element

import javax.swing.Icon

import com.intellij.psi.PsiElement
import com.intellij.util.PlatformIcons
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.structureView.element.Block.childrenOf

private class Block(block: ScBlock) extends AbstractTreeElement(block) {
  override def getIcon(open: Boolean): Icon = PlatformIcons.CLASS_INITIALIZER

  override def children: Seq[PsiElement] = childrenOf(block)
}

private object Block {
  def childrenOf(block: ScBlock): Seq[PsiElement] = block.getChildren.collect {
    case element @ (_: ScFunction | _: ScTypeDefinition | _: ScBlockExpr) => element
  }
}
