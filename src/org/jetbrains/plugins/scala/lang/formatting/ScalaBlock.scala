package org.jetbrains.plugins.scala.lang.formatting

import psi._
import impl.expr.ScBlockImpl
import psi.api.ScalaFile
import settings.ScalaCodeStyleSettings
import com.intellij.formatting._
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.lang.formatting.processors._

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._


import java.util.List;
import java.util.ArrayList;

class ScalaBlock (val myParentBlock: ScalaBlock,
        private val myNode: ASTNode,
        val myLastNode: ASTNode,
        private var myAlignment: Alignment,
        private var myIndent: Indent,
        private var myWrap: Wrap,
        private val mySettings: CodeStyleSettings)
extends Object with ScalaTokenTypes with Block {

  private var mySubBlocks: List[Block] = null

  def getNode = myNode

  def getSettings = mySettings

  def getTextRange = if (myLastNode == null) myNode.getTextRange
  else {
    new TextRange(myNode.getTextRange.getStartOffset, myLastNode.getTextRange.getEndOffset)
  }

  def getIndent = myIndent

  def getWrap = myWrap

  def getAlignment = myAlignment

  def isLeaf = isLeaf(myNode)

  def isIncomplete = isIncomplete(myNode)

  def getChildAttributes(newChildIndex: Int): ChildAttributes = {
    val scalaSettings = mySettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val parent = getNode.getPsi
    parent match {
      case _: ScBlockExpr | _: ScTemplateBody | _: ScForStatement  | _: ScWhileStmt |
           _: ScTryBlock | _: ScCatchBlock | _: ScPackaging | _: ScMatchStmt => {
        return new ChildAttributes(Indent.getNormalIndent, null)
      }
      case _: ScBlock => new ChildAttributes(Indent.getNoneIndent, null)
      case _: ScIfStmt => return new ChildAttributes(Indent.getNormalIndent, this.getAlignment)
      case x: ScDoStmt => {
        if (x.hasExprBody)
          return new ChildAttributes(Indent.getNoneIndent(), null)
        else return new ChildAttributes(Indent.getNormalIndent, null)
      }
      case _: ScXmlElement => return new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScalaFile => return new ChildAttributes(Indent.getNoneIndent, null)
      case _: ScCaseClause => return new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScExpression | _: ScPattern | _: ScParameters =>
        return new ChildAttributes(Indent.getContinuationWithoutFirstIndent, this.getAlignment)
      case _ => new ChildAttributes(Indent.getNoneIndent(), null)
    }
  }

  def getSpacing(child1: Block, child2: Block) = {
    ScalaSpacingProcessor.getSpacing(child1.asInstanceOf[ScalaBlock], child2.asInstanceOf[ScalaBlock])
  }

  def getSubBlocks: List[Block] = {
    if (mySubBlocks == null && myLastNode == null) {
      mySubBlocks = getDummyBlocks(myNode, this)
    } else if (mySubBlocks == null) {
      mySubBlocks = getDummyBlocks(myNode, myLastNode, this)
    }
    mySubBlocks
  }

  def isLeaf(node: ASTNode): Boolean = {
    if (myLastNode == null) return node.getFirstChildNode() == null
    else return false
  }

  def isIncomplete(node: ASTNode): Boolean = {
    if (node.getPsi.isInstanceOf[PsiErrorElement])
      return true;
    var lastChild = node.getLastChildNode();
    while (lastChild != null &&
      (lastChild.getPsi.isInstanceOf[PsiWhiteSpace] || lastChild.getPsi.isInstanceOf[PsiComment])) {
      lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null) {
      return false;
    }
    if (lastChild.getPsi.isInstanceOf[PsiErrorElement]) {
      return true;
    }
    return isIncomplete(lastChild);
  }

}