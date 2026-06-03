package org.jetbrains.plugins.scala.structureView.element

import com.intellij.psi.PsiElement
import com.intellij.ui.{IconManager, PlatformIcons}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.*
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.*
import org.jetbrains.plugins.scala.structureView.element.Block.childrenOf

import javax.swing.Icon

private class Block(block: ScBlock) extends AbstractTreeElementDelegatingChildrenToPsi(block) {
  override def getIcon(open: Boolean): Icon = IconManager.getInstance.getPlatformIcon(PlatformIcons.ClassInitializer)

  override def getPresentableText: String = ""

  override def children: Seq[PsiElement] = childrenOf(block)

  override def isAlwaysLeaf: Boolean = false
}

private object Block {
  def childrenOf(block: ScBlock): Seq[PsiElement] = {
    val blockChildren = block.getChildren
    blockChildren.collect {
      case element @ (_: ScFunction | _: ScTypeDefinition | _: ScBlockExpr | _: ScExtension) => element
    }.toSeq
  }
}
