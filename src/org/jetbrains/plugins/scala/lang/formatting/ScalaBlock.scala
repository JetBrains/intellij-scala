package org.jetbrains.plugins.scala
package lang
package formatting

import psi.api.ScalaFile
import settings.ScalaCodeStyleSettings
import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.{CommonCodeStyleSettings, CodeStyleSettings};
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._


import java.util.List;


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

  def getTextRange =
    if (myLastNode == null) myNode.getTextRange
    else new TextRange(myNode.getTextRange.getStartOffset, myLastNode.getTextRange.getEndOffset)

  def getIndent = myIndent

  def getWrap = myWrap

  def getAlignment = myAlignment

  def isLeaf = isLeaf(myNode)

  def isIncomplete = isIncomplete(myNode)

  def getChildAttributes(newChildIndex: Int): ChildAttributes = {
    val scalaSettings = mySettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val indentSize = mySettings.getIndentSize(ScalaFileType.SCALA_FILE_TYPE)
    val parent = getNode.getPsi
    val braceShifted = mySettings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    parent match {
      case m: ScMatchStmt => {
        if (m.caseClauses.length == 0) {
          return new ChildAttributes(if (braceShifted) Indent.getNoneIndent else Indent.getNormalIndent, null)
        } else {
          val indent = if (mySettings.INDENT_CASE_FROM_SWITCH) Indent.getSpaceIndent(2 * indentSize)
          else Indent.getNormalIndent
          return new ChildAttributes(indent, null)
        }
      }
      case c: ScCaseClauses => return new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScBlockExpr | _: ScTemplateBody | _: ScForStatement  | _: ScWhileStmt |
           _: ScTryBlock | _: ScCatchBlock => {
        return new ChildAttributes(if (braceShifted) Indent.getNoneIndent else Indent.getNormalIndent, null)
      }
      case p : ScPackaging if p.isExplicit => new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScBlock => new ChildAttributes(Indent.getNoneIndent, null)
      case _: ScIfStmt => return new ChildAttributes(Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE),
        this.getAlignment)
      case x: ScDoStmt => {
        if (x.hasExprBody)
          return new ChildAttributes(Indent.getNoneIndent(), null)
        else return new ChildAttributes(if (mySettings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED)
          Indent.getNoneIndent else Indent.getNormalIndent, null)
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
    import collection.JavaConversions._
    if (mySubBlocks == null && myLastNode == null) {
      mySubBlocks = getDummyBlocks(myNode, this).filterNot(
        _.asInstanceOf[ScalaBlock].getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR)
    } else if (mySubBlocks == null) {
      mySubBlocks = getDummyBlocks(myNode, myLastNode, this).filterNot(
        _.asInstanceOf[ScalaBlock].getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR)
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

  private var _suggestedWrap: Wrap = null
  def suggestedWrap: Wrap = {
    if (_suggestedWrap == null) {
      val settings = getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      _suggestedWrap = ScalaWrapManager.suggestedWrap(this, settings)
    }
    _suggestedWrap
  }

}