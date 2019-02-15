package org.jetbrains.plugins.scala
package lang
package formatting

import java.util

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigUtil
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.collection.JavaConverters._

class ScalaBlock(val myParentBlock: ScalaBlock,
                 protected val myNode: ASTNode,
                 val myLastNode: ASTNode,
                 protected var myAlignment: Alignment,
                 protected var myIndent: Indent,
                 protected var myWrap: Wrap,
                 protected val mySettings: CodeStyleSettings,
                 val subBlocksContext: Option[SubBlocksContext] = None)
  extends ASTBlock with ScalaTokenTypes {

  protected var mySubBlocks: util.List[Block] = _

  def getSettings: CodeStyleSettings = mySettings

  def getCommonSettings: CommonCodeStyleSettings = mySettings.getCommonSettings(ScalaLanguage.INSTANCE)

  override def getNode: ASTNode = myNode

  override def getTextRange: TextRange =
    if (myLastNode == null) myNode.getTextRange
    else new TextRange(myNode.getTextRange.getStartOffset, myLastNode.getTextRange.getEndOffset)

  override def getIndent: Indent = myIndent

  override def getWrap: Wrap = myWrap

  override def getAlignment: Alignment = myAlignment

  override def isLeaf: Boolean = isLeaf(myNode)

  override def isIncomplete: Boolean = isIncomplete(myNode)

  override def getChildAttributes(newChildIndex: Int): ChildAttributes = {
    val scalaSettings = mySettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val parent = getNode.getPsi

    if (scalaSettings.USE_SCALAFMT_FORMATTER) {
      getChildAttributesScalafmtInner(newChildIndex, parent)
    } else {
      getChildAttributesIntellijInner(newChildIndex, scalaSettings)
    }
  }

  private def getChildAttributesIntellijInner(newChildIndex: Int, scalaSettings: ScalaCodeStyleSettings): ChildAttributes = {
    val parent = getNode.getPsi

    val indentSize = mySettings.getIndentSize(ScalaFileType.INSTANCE)
    val braceShifted = mySettings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED

    def isBlockOnlyScope(scope: PsiElement): Boolean = !isLeaf &&
      Set(ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tLPARENTHESIS).contains(scope.getNode.getElementType) &&
      (scope.getParent match {
        case _: ScTryBlock | _: ScFor | _: ScPackaging => true
        case _ => false
      })

    parent match {
      case m: ScMatch =>
        if (m.clauses.isEmpty) {
          new ChildAttributes(if (braceShifted) Indent.getNoneIndent else Indent.getNormalIndent, null)
        } else {
          val indent = Indent.getSpaceIndent(2 * indentSize)
          new ChildAttributes(indent, null)
        }
      case _: ScCaseClauses =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case l: ScLiteral
        if l.isMultiLineString && scalaSettings.MULTILINE_STRING_SUPORT != ScalaCodeStyleSettings.MULTILINE_STRING_NONE =>
        new ChildAttributes(Indent.getSpaceIndent(3, true), null)
      case b: ScBlockExpr if b.lastExpr.exists(_.isInstanceOf[ScFunctionExpr]) =>
        var i = getSubBlocks().size() - newChildIndex
        val elem = b.lastExpr.get.getNode.getTreePrev
        if (elem.getElementType != TokenType.WHITE_SPACE || !elem.getText.contains("\n")) i = 0
        val indent = i + (if (!braceShifted) 1 else 0)
        new ChildAttributes(Indent.getSpaceIndent(indent * indentSize), null)
      case _: ScBlockExpr | _: ScEarlyDefinitions | _: ScTemplateBody | _: ScFor | _: ScWhile |
           _: ScTryBlock | _: ScCatchBlock =>
        new ChildAttributes(if (braceShifted) Indent.getNoneIndent
        else if (mySubBlocks != null && mySubBlocks.size >= newChildIndex &&
          mySubBlocks.get(newChildIndex - 1).isInstanceOf[ScalaBlock] &&
          mySubBlocks.get(newChildIndex - 1).asInstanceOf[ScalaBlock].getNode.getElementType == ScalaElementType.CASE_CLAUSES)
          Indent.getSpaceIndent(2 * indentSize)
        else
          Indent.getNormalIndent, null)
      case scope if isBlockOnlyScope(scope) =>
        new ChildAttributes(
          if (scope.getNode.getElementType == ScalaTokenTypes.tLBRACE && braceShifted) Indent.getNoneIndent
          else Indent.getNormalIndent,
          null)
      case p: ScPackaging if p.isExplicit =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScBlock =>
        val grandParent = parent.getParent
        new ChildAttributes(if (grandParent != null && (grandParent.isInstanceOf[ScCaseClause] || grandParent.isInstanceOf[ScFunctionExpr])) Indent.getNormalIndent
        else Indent.getNoneIndent, null)
      case _: ScIf =>
        new ChildAttributes(Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE), this.getAlignment)
      case x: ScDo =>
        if (x.body.isDefined)
          new ChildAttributes(Indent.getNoneIndent, null)
        else new ChildAttributes(if (mySettings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED)
          Indent.getNoneIndent
        else Indent.getNormalIndent, null)
      case _: ScXmlElement =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScalaFile =>
        new ChildAttributes(Indent.getNoneIndent, null)
      case _: ScCaseClause =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScExpression | _: ScPattern | _: ScParameters =>
        new ChildAttributes(Indent.getContinuationWithoutFirstIndent, this.getAlignment)
      case _: ScDocComment =>
        new ChildAttributes(Indent.getSpaceIndent(if (scalaSettings.USE_SCALADOC2_FORMATTING) 2 else 1), null)
      case _ if parent.getNode.getElementType == ScalaTokenTypes.kIF =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case p: ScParameterClause if scalaSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS && isConstructorArgOrMemberFunctionParameter(p) =>
        new ChildAttributes(
          Indent.getSpaceIndent(scalaSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS, false), null)
      case _: ScParameterClause =>
        new ChildAttributes(
          if (scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS) Indent.getNormalIndent
          else Indent.getContinuationWithoutFirstIndent, this.getAlignment)
      case _: ScValue =>
        new ChildAttributes(Indent.getNormalIndent, this.getAlignment) //by default suppose there will be simple expr
      case _: ScArgumentExprList =>
        new ChildAttributes(Indent.getNormalIndent, this.getAlignment)
      case _ =>
        new ChildAttributes(Indent.getNoneIndent, null)
    }
  }

  private def getChildAttributesScalafmtInner(newChildIndex: Int, parent: PsiElement): ChildAttributes = {
    val (indentDefn, indentCall) = ScalafmtDynamicConfigUtil.configOptForFile(parent.getContainingFile) match {
      case Some(config) => (config.continuationIndentDefnSite, config.continuationIndentCallSite)
      case None => (2, 4)
    }
    parent match {
      case _: ScParameterClause if newChildIndex != 0 =>
        new ChildAttributes(Indent.getSpaceIndent(indentDefn), null)
      case _: ScArguments if newChildIndex != 0 =>
        new ChildAttributes(Indent.getSpaceIndent(indentCall), null)
      case m: ScMatch if m.clauses.nonEmpty =>
        new ChildAttributes(Indent.getSpaceIndent(4), null)
      case _: ScBlock | _: ScTemplateBody | _: ScMatch | _: ScCaseClauses | _: ScCaseClause =>
        new ChildAttributes(Indent.getSpaceIndent(2), null)
      case _ if parent.getNode.getElementType == ScalaTokenTypes.kIF =>
        new ChildAttributes(Indent.getSpaceIndent(2), null)
      case _ =>
        new ChildAttributes(Indent.getNoneIndent, null)
    }
  }

  private def isConstructorArgOrMemberFunctionParameter(paramClause: ScParameterClause): Boolean = {
    val owner = paramClause.owner
    owner != null && (owner.isInstanceOf[ScPrimaryConstructor] || owner.isInstanceOf[ScFunction])
  }

  override def getSpacing(child1: Block, child2: Block): Spacing = {
    ScalaSpacingProcessor.getSpacing(child1.asInstanceOf[ScalaBlock], child2.asInstanceOf[ScalaBlock])
  }

  override def getSubBlocks: util.List[Block] = {
    if (mySubBlocks == null) {
      mySubBlocks = getDummyBlocks(myNode, myLastNode, this)
        .asScala
        .filterNot {
          _.asInstanceOf[ScalaBlock].getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE
        }
        .asJava
    }
    mySubBlocks
  }

  def isLeaf(node: ASTNode): Boolean = {
    if (myLastNode == null) node.getFirstChildNode == null
    else false
  }

  private def isIncomplete(node: ASTNode): Boolean = {
    if (node.getPsi.isInstanceOf[PsiErrorElement])
      return true

    val lastChild = findLastNonBlankChild(node)
    if (lastChild == null) {
      false
    } else {
      isIncomplete(lastChild)
    }
  }

  private def findLastNonBlankChild(node: ASTNode): ASTNode = {
    var lastChild = node.getLastChildNode
    while (lastChild != null &&
      (lastChild.getPsi.isInstanceOf[PsiWhiteSpace] || lastChild.getPsi.isInstanceOf[PsiComment])) {
      lastChild = lastChild.getTreePrev
    }
    lastChild
  }

  private var _suggestedWrap: Wrap = _

  def suggestedWrap: Wrap = {
    if (_suggestedWrap == null) {
      val settings = getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      _suggestedWrap = ScalaWrapManager.suggestedWrap(this, settings)
    }
    _suggestedWrap
  }

  def getChildBlockLastNode(childNode: ASTNode): ASTNode = subBlocksContext.flatMap(_.childrenAdditionalContexts.get(childNode)).
    map(_.getLastNode(childNode)).orNull

  def getCustomAlignment(childNode: ASTNode): Option[Alignment] = subBlocksContext
    .flatMap(_.childrenAdditionalContexts.get(childNode)).flatMap(_.alignment)
}

private[formatting]
class SubBlocksContext(val additionalNodes: Seq[ASTNode] = Seq(),
                       val alignment: Option[Alignment] = None,
                       val childrenAdditionalContexts: Map[ASTNode, SubBlocksContext] = Map()) {
  def getLastNode(firstNode: ASTNode): ASTNode =
    getLastNode.filter(_ != firstNode).orNull

  private def getLastNode: Option[ASTNode] =
    childrenAdditionalContexts.map { case (_, context) => context.getLastNode }.filter(_.isDefined).map(_.get) ++
      additionalNodes ++ childrenAdditionalContexts.map { case (child, _) => child } match {
      case empty if empty.isEmpty => None
      case nonEmpty => Some(nonEmpty.maxBy(_.getTextRange.getEndOffset))
    }
}

private[formatting]
object SubBlocksContext {
  def apply(childNodes: Seq[ASTNode], alignment: Option[Alignment]): SubBlocksContext =
    new SubBlocksContext(childNodes, alignment)

  def apply(node: ASTNode, alignment: Alignment, childNodes: Seq[ASTNode]): SubBlocksContext = {
    val children = Map(node -> new SubBlocksContext(childNodes, Some(alignment)))
    new SubBlocksContext(Seq(), None, children)
  }
}
