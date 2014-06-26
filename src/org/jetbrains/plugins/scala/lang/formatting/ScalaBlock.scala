package org.jetbrains.plugins.scala
package lang
package formatting

import psi.api.ScalaFile
import settings.ScalaCodeStyleSettings
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.{CommonCodeStyleSettings, CodeStyleSettings}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import java.util.List
import scaladoc.psi.api.ScDocComment
import psi.api.toplevel.ScEarlyDefinitions
import com.intellij.formatting._
import com.intellij.psi._
import psi.api.base.ScLiteral
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import scala.Some
import org.jetbrains.plugins.scala.lang.formatting.automatic.settings.matching.ScalaFormattingRuleMatcher
import org.jetbrains.plugins.scala.lang.formatting.automatic.ScalaAutoFormatter

class ScalaBlock (val myParentBlock: ScalaBlock,
                  protected val myNode: ASTNode,
                  val myLastNode: ASTNode,
                  protected var myAlignment: Alignment,
                  protected var myIndent: Indent,
                  protected var myWrap: Wrap,
                  protected val mySettings: CodeStyleSettings)
        extends Object with ScalaTokenTypes with Block {

  protected var mySubBlocks: List[Block] = null

  def getNode = myNode

  def getSettings = mySettings

  def getCommonSettings = mySettings.getCommonSettings(ScalaFileType.SCALA_LANGUAGE)

  def getTextRange =
    if (myLastNode == null) myNode.getTextRange
    else new TextRange(myNode.getTextRange.getStartOffset, myLastNode.getTextRange.getEndOffset)

  //TODO: replace these test stubs
  def getIndent =
    if (ScalaBlock.myFormatter != null) {
      ScalaBlock.myFormatter.getIndent(this)
    } else {
      myIndent
    }

  def getWrap =
    if (ScalaBlock.myFormatter != null) {
      ScalaBlock.myFormatter.getWrap(this)
    } else {
      myWrap
    }

  def getAlignment =
    if (ScalaBlock.myFormatter != null) {
      ScalaBlock.myFormatter.getAlignment(this)
    } else {
      myAlignment
    }

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
        new ChildAttributes(if (braceShifted) Indent.getNoneIndent else Indent.getNormalIndent, null)
      case p : ScPackaging if p.isExplicit => new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScBlock =>
        val grandParent = parent.getParent
        new ChildAttributes(if (grandParent != null && grandParent.isInstanceOf[ScCaseClause]) Indent.getNormalIndent
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
      case _ => new ChildAttributes(Indent.getNoneIndent, null)
    }
  }

  def getSpacing(child1: Block, child2: Block) = if (ScalaBlock.myFormatter != null) {
    ScalaBlock.myFormatter.getSpacing(child1, child2)
  } else ScalaSpacingProcessor.getSpacing(child1.asInstanceOf[ScalaBlock], child2.asInstanceOf[ScalaBlock])

  def getSubBlocks(): List[Block] = {
    import collection.JavaConversions._
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

  def getPrevNonWSNode: ASTNode = {
    @tailrec
    def helper(node: ASTNode): ASTNode = node match {
      case _: PsiWhiteSpace => helper(node.getTreePrev)
      case _ => node
    }
    helper(myNode.getTreePrev)
  }

  def getInitialSpacing: Option[PsiWhiteSpace] = {
    getPrevPsi(myNode.getPsi) match {
      case Some(prevSibling) => lastLeaf(prevSibling) match {
        case spacing: PsiWhiteSpace => Some(spacing)
        case _ => None
      }
      case None => None
    }
  }

  def getInitialWhiteSpace: String = getInitialSpacing match {
    case Some(spacing) => spacing.getText
    case one => ""
  }

  def getInLineOffset: Int = {
    val document = PsiDocumentManager.getInstance(getNode.getPsi.getProject).getDocument(getNode.getPsi.getContainingFile)
    val startOffset = getTextRange.getStartOffset
    startOffset - document.getLineStartOffset(document.getLineNumber(startOffset))
  }

  def isFirstInFile: Boolean = getTextRange.getStartOffset == 0

  def isOnNewLine: Boolean = getInitialWhiteSpace.contains("\n") || isFirstInFile

  def getLeadingWhitespaceSize: Int = {
    val space = getInitialWhiteSpace
    space.substring(space.lastIndexOf("\n" + 1)).length
  }

  /**
   * Returns relative indent length with indent counted from direct parent.
   * @return indent length in space characters
   */
  def getIndentFromDirectParent: Int = {
    val initialWS = getInitialWhiteSpace
    initialWS.substring(initialWS.lastIndexOf("\n") match {
      case -1 => 0
      case lastIndex: Int => lastIndex + 1
    }).length - myParentBlock.getInLineOffset
  }

  /**
   * Returns relative indent length with indent counted from first ancestor located on new line.
   * @return indent lendth in space characters
   */
  def getIndentFromNewlineAncestor: Option[Int] = {
    @tailrec
    def helper(block: ScalaBlock, startOffset: Int, startLineNumber: Int): Option[Int] = {
      val parent = block.myParentBlock
      if (parent == null) {
        return None
      }
      if (parent.isOnNewLine && parent.getInLineOffset < startOffset) {
        Some(startOffset - parent.getInLineOffset)
      } else {
        helper(parent, startOffset, startLineNumber)
      }
    }
    helper(this, getInLineOffset, getLineNumber)
  }

  def getLineNumber: Int = PsiDocumentManager.getInstance(getNode.getPsi.getProject).
          getDocument(getNode.getPsi.getContainingFile).getLineNumber(getTextRange.getStartOffset)

  //  /**
  //   * Checks whether this block crosses right margin with spacings that are currently present in the document.
  //   * @return true if block crosses right margin, false otherwise
  //   */
  //  def crossesRightMargin: Boolean = crossesRightMargin(getNode)
  //  {
  //    val node = getNode
  //    val lines = node.getText.split("\n")
  //    val rightMargin: Int = getSettings.RIGHT_MARGIN
  //    val document = PsiDocumentManager.getInstance(node.getPsi.getProject).getDocument(node.getPsi.getContainingFile)
  //    //since block's text is continuous, sophisticated checks are required for first and last lines
  //    //for inner lines it is enough to check whether their length is less then right margin
  //    val firstLineCrosses = (node.getStartOffset + lines(0).length -
  //      document.getLineStartOffset(document.getLineNumber(getTextRange.getStartOffset))) > rightMargin
  //    val lastLineCrosses = lines(lines.size - 1).length > rightMargin
  //    firstLineCrosses && lastLineCrosses && lines.slice(1, lines.size - 1).exists(_.length > rightMargin)
  //  }

  def wouldCrossRightMargin: Boolean = getPrevPsi(myNode.getPsi) match {
    case Some(psi) =>
      val document = PsiDocumentManager.getInstance(getNode.getPsi.getProject).getDocument(getNode.getPsi.getContainingFile)
//      val myLineNumber = document.getLineNumber(myNode.getTextRange.getStartOffset)
      val psiTextRange = psi.getTextRange
      val prevLineOffset = if (psi.isInstanceOf[PsiWhiteSpace]) psiTextRange.getStartOffset else psiTextRange.getEndOffset
//      val psiLineNumber = document.getLineNumber(prevLineOffset)
//      if (myLineNumber == psiLineNumber && getNode.getTextRange.getEndOffset - document.getLineStartOffset(document.getLineNumber(prevLineOffset)) > getSettings.RIGHT_MARGIN) return false
      val text = getNode.getText
      val newLine = text.indexOf("\n")
      val length = if (newLine >= 0) text.substring(0, newLine).length else text.length
      val resultingOffset = prevLineOffset + length
      resultingOffset - document.getLineStartOffset(document.getLineNumber(prevLineOffset)) > getSettings.RIGHT_MARGIN
    case None => false
  }
}

object ScalaBlock {
  //TODO: remove this. This is a temporary means of testing used before serialization/deserialization is implemented properly
  // Actually all the ScalaBlock.scala file should be rewritten as blocks should be constructed and not altered during formatting
  protected var myFormatter: ScalaAutoFormatter = null

  def initFormatter(learnRoot: ScalaBlock) = myFormatter = new ScalaAutoFormatter(ScalaFormattingRuleMatcher.getDefaultMatcherAndMatch(learnRoot))

  def feedFormatter(formatRoot: ScalaBlock) = myFormatter.runMatcher(formatRoot)
}