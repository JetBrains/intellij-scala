package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings

import java.util

private class StringLineScalaBlock(
  myTextRange: TextRange,
  mainNode: ASTNode,
  myAlignment: Alignment,
  myIndent: Indent,
  myWrap: Wrap,
  mySettings: CodeStyleSettings
) extends ScalaBlock(mainNode, null, myAlignment, myIndent, myWrap, mySettings) {

  override def getTextRange: TextRange = myTextRange
  override def isLeaf = true
  override def isLeaf(node: ASTNode): Boolean = true
  override def getChildAttributes(newChildIndex: Int): ChildAttributes = new ChildAttributes(Indent.getNoneIndent, null)
  override def getSpacing(child1: Block, child2: Block): Spacing = Spacing.getReadOnlySpacing
  override def getSubBlocks: util.List[Block] = {
    if (subBlocks == null) {
      subBlocks = new util.ArrayList[Block]()
    }
    subBlocks
  }
}