package org.jetbrains.plugins.scala
package lang
package formatting

import java.util.List

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.{PsiComment, PsiErrorElement, PsiWhiteSpace, TokenType}
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

class ScalaBlock (val myParentBlock: ScalaBlock,
        protected val myNode: ASTNode,
        val myLastNode: ASTNode,
        protected var myAlignment: Alignment,
        protected var myIndent: Indent,
        protected var myWrap: Wrap,
        protected val mySettings: CodeStyleSettings)
extends Object with ScalaTokenTypes with ASTBlock {

  protected var mySubBlocks: List[Block] = null

  override def getNode = myNode

  def getSettings = mySettings

  def getCommonSettings = mySettings.getCommonSettings(ScalaFileType.SCALA_LANGUAGE)

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
          new ChildAttributes(if (braceShifted) Indent.getNoneIndent else Indent.getNormalIndent, null)
        } else {
          val indent = if (mySettings.INDENT_CASE_FROM_SWITCH) Indent.getSpaceIndent(2 * indentSize)
          else Indent.getNormalIndent
          new ChildAttributes(indent, null)
        }
      }
      case c: ScCaseClauses => new ChildAttributes(Indent.getNormalIndent, null)
      case l: ScLiteral
        if l.isMultiLineString && scalaSettings.MULTILINE_STRING_SUPORT != ScalaCodeStyleSettings.MULTILINE_STRING_NONE =>
        new ChildAttributes(Indent.getSpaceIndent(3, true), null)
      case b: ScBlockExpr if b.lastExpr.exists(_.isInstanceOf[ScFunctionExpr]) =>
        var i = getSubBlocks.size() - newChildIndex
        val elem = b.lastExpr.get.getNode.getTreePrev
        if (elem.getElementType != TokenType.WHITE_SPACE || !elem.getText.contains("\n")) i = 0
        val indent = i + (if (!braceShifted) 1 else 0)
        new ChildAttributes(Indent.getSpaceIndent(indent * indentSize), null)
      case _: ScBlockExpr | _: ScEarlyDefinitions | _: ScTemplateBody | _: ScForStatement  | _: ScWhileStmt |
           _: ScTryBlock | _: ScCatchBlock =>
        new ChildAttributes(if (braceShifted) Indent.getNoneIndent else
        if (mySubBlocks != null && mySubBlocks.size >= newChildIndex &&
                mySubBlocks.get(newChildIndex - 1).isInstanceOf[ScalaBlock] &&
                mySubBlocks.get(newChildIndex - 1).asInstanceOf[ScalaBlock].getNode.getElementType == ScalaElementTypes.CASE_CLAUSES)
          Indent.getSpaceIndent(2 * indentSize)
        else
          Indent.getNormalIndent, null)
      case p : ScPackaging if p.isExplicit => new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScBlock =>
        val grandParent = parent.getParent
        new ChildAttributes(if (grandParent != null && (grandParent.isInstanceOf[ScCaseClause] || grandParent.isInstanceOf[ScFunctionExpr])) Indent.getNormalIndent
        else Indent.getNoneIndent, null)
      case _: ScIfStmt => new ChildAttributes(Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE),
        this.getAlignment)
      case x: ScDoStmt => {
        if (x.hasExprBody)
          new ChildAttributes(Indent.getNoneIndent, null)
        else new ChildAttributes(if (mySettings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED)
          Indent.getNoneIndent else Indent.getNormalIndent, null)
      }
      case _: ScXmlElement => new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScalaFile => new ChildAttributes(Indent.getNoneIndent, null)
      case _: ScCaseClause => new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScExpression | _: ScPattern | _: ScParameters =>
        new ChildAttributes(Indent.getContinuationWithoutFirstIndent, this.getAlignment)
      case comment: ScDocComment if comment.version > 1 =>
        new ChildAttributes(Indent.getSpaceIndent(2), null)
      case _: ScDocComment =>
        new ChildAttributes(Indent.getSpaceIndent(1), null)
      case _ if parent.getNode.getElementType == ScalaTokenTypes.kIF =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScParameterClause =>
        new ChildAttributes(if (scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS) Indent.getNormalIndent
          else Indent.getContinuationWithoutFirstIndent, this.getAlignment)
      case _ => new ChildAttributes(Indent.getNoneIndent, null)
    }
  }

  def getSpacing(child1: Block, child2: Block) = {
    ScalaSpacingProcessor.getSpacing(child1.asInstanceOf[ScalaBlock], child2.asInstanceOf[ScalaBlock])
  }

  def getSubBlocks(): List[Block] = {
    import scala.collection.JavaConversions._
    if (mySubBlocks == null) {
      mySubBlocks = getDummyBlocks(myNode, myLastNode, this).filterNot {
        _.asInstanceOf[ScalaBlock].getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE
      }
    }
    mySubBlocks
  }

  def isLeaf(node: ASTNode): Boolean = {
    if (myLastNode == null) node.getFirstChildNode == null
    else false
  }

  def isIncomplete(node: ASTNode): Boolean = {
    if (node.getPsi.isInstanceOf[PsiErrorElement])
      return true
    var lastChild = node.getLastChildNode
    while (lastChild != null &&
      (lastChild.getPsi.isInstanceOf[PsiWhiteSpace] || lastChild.getPsi.isInstanceOf[PsiComment])) {
      lastChild = lastChild.getTreePrev
    }
    if (lastChild == null) {
      return false
    }
    if (lastChild.getPsi.isInstanceOf[PsiErrorElement]) {
      return true
    }
    isIncomplete(lastChild)
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