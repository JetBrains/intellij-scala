package org.jetbrains.plugins.scala
package lang
package formatting

import java.util

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.isConstructorArgOrMemberFunctionParameter
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

class ScalaBlock(val parentBlock: ScalaBlock,
                 val node: ASTNode,
                 val lastNode: ASTNode,
                 val alignment: Alignment,
                 val indent: Indent,
                 val wrap: Wrap,
                 val settings: CodeStyleSettings,
                 val subBlocksContext: Option[SubBlocksContext] = None)
  extends ASTBlock with ScalaTokenTypes {

  protected var subBlocks: util.List[Block] = _

  def commonSettings: CommonCodeStyleSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE)

  override def getNode: ASTNode = node

  override def getTextRange: TextRange =
    if (lastNode == null) node.getTextRange
    else new TextRange(node.getTextRange.getStartOffset, lastNode.getTextRange.getEndOffset)

  override def getIndent: Indent = indent

  override def getWrap: Wrap = wrap

  override def getAlignment: Alignment = alignment

  override def isLeaf: Boolean = isLeaf(node)

  override def isIncomplete: Boolean = isIncomplete(node)

  override def getChildAttributes(newChildIndex: Int): ChildAttributes = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val parent = getNode.getPsi

    if (scalaSettings.USE_SCALAFMT_FORMATTER) {
      getChildAttributesScalafmtInner(newChildIndex, parent)
    } else {
      getChildAttributesIntellijInner(newChildIndex, scalaSettings)
    }
  }

  private def getChildAttributesIntellijInner(newChildIndex: Int, scalaSettings: ScalaCodeStyleSettings): ChildAttributes = {
    val parent = getNode.getPsi

    val indentSize = settings.getIndentSize(ScalaFileType.INSTANCE)
    val braceShifted = settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED

    object ElementType {
      def unapply(psi: PsiElement): Some[IElementType] =
        Some(psi.getNode.getElementType)
    }

    parent match {
      case m: ScMatch =>
        val indent =
          if (m.clauses.nonEmpty) Indent.getSpaceIndent(2 * indentSize)
          else if (braceShifted) Indent.getNoneIndent
          else Indent.getNormalIndent
        new ChildAttributes(indent, null)
      case _: ScCaseClauses =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case l: ScLiteral if l.isMultiLineString && scalaSettings.supportMultilineString =>
        new ChildAttributes(Indent.getSpaceIndent(3, true), null)
      case b: ScBlockExpr if b.resultExpression.exists(_.isInstanceOf[ScFunctionExpr]) || b.caseClauses.isDefined =>
        val indent = {
          val nodeBeforeLast = b.resultExpression.orElse(b.caseClauses).get.getNode.getTreePrev
          val isLineBreak = nodeBeforeLast.getElementType == TokenType.WHITE_SPACE && nodeBeforeLast.textContains('\n')
          val extraIndent =
            if (isLineBreak) getSubBlocks().size - newChildIndex
            else 0
          val indentsCount = extraIndent + (if (braceShifted) 0 else 1)
          Indent.getSpaceIndent(indentsCount * indentSize)
        }
        new ChildAttributes(indent, null)
      case _: ScBlockExpr | _: ScEarlyDefinitions | _: ScTemplateBody |
           _: ScFor | _: ScWhile | _: ScCatchBlock | ElementType(ScalaTokenTypes.kYIELD | ScalaTokenTypes.kDO) =>
        val indent =
          if (braceShifted) {
            Indent.getNoneIndent
          } else {
            Indent.getNormalIndent
          }
        new ChildAttributes(indent, null)
      case scope if isBlockOnlyScope(scope) =>
        val indent =
          if (scope.getNode.getElementType == ScalaTokenTypes.tLBRACE && braceShifted) Indent.getNoneIndent
          else Indent.getNormalIndent
        new ChildAttributes(indent, null)
      case p: ScPackaging if p.isExplicit =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScBlock =>
        val grandParent = parent.getParent
        val indent = grandParent match {
          case _: ScCaseClause | _: ScFunctionExpr => Indent.getNormalIndent
          case _  => Indent.getNoneIndent
        }
        new ChildAttributes(indent, null)
      case _: ScIf =>
        new ChildAttributes(Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE), this.getAlignment)
      case x: ScDo =>
        val indent =
          if (x.body.isDefined) Indent.getNoneIndent
          else if (settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED) Indent.getNoneIndent
          else Indent.getNormalIndent
        new ChildAttributes(indent, null)
      case _: ScXmlElement =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScalaFile =>
        new ChildAttributes(Indent.getNoneIndent, null)
      case _: ScCaseClause =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScMethodCall if newChildIndex > 0 =>
        val prevChildBlock = getSubBlocks.get(newChildIndex - 1)
        new ChildAttributes(prevChildBlock.getIndent, prevChildBlock.getAlignment)
      case _: ScExpression | _: ScPattern | _: ScParameters =>
        new ChildAttributes(Indent.getContinuationWithoutFirstIndent, this.getAlignment)
      case _: ScDocComment =>
        val indent = Indent.getSpaceIndent(if (scalaSettings.USE_SCALADOC2_FORMATTING) 2 else 1)
        new ChildAttributes(indent, null)
      case ElementType(ScalaTokenTypes.kIF) =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case ElementType(ScalaTokenTypes.kELSE) =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case p: ScParameterClause
        if scalaSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS && isConstructorArgOrMemberFunctionParameter(p) =>
        val indent = Indent.getSpaceIndent(scalaSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS, false)
        new ChildAttributes(indent, null)
      case _: ScParameterClause =>
        val indent =
          if (scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS) Indent.getNormalIndent
          else Indent.getContinuationWithoutFirstIndent
        new ChildAttributes(indent, this.getAlignment)
      case _: ScValue =>
        new ChildAttributes(Indent.getNormalIndent, this.getAlignment) //by default suppose there will be simple expr
      case _: ScArgumentExprList =>
        new ChildAttributes(Indent.getNormalIndent, this.getAlignment)
      // def, var, val, type, given + `=`
      case _: ScDefinitionWithAssignment =>
        new ChildAttributes(Indent.getNormalIndent, this.getAlignment)
      case _ =>
        new ChildAttributes(Indent.getNoneIndent, null)
    }
  }

  private def isBlockOnlyScope(scope: PsiElement): Boolean = {
    !isLeaf && ScalaTokenTypes.LBRACE_LPARENT_TOKEN_SET.contains(scope.getNode.getElementType) &&
      (scope.getParent match {
        case _: ScFor | _: ScPackaging => true
        case _ => false
      })
  }

  private def getChildAttributesScalafmtInner(newChildIndex: Int, parent: PsiElement): ChildAttributes = {
    val file = parent.getContainingFile
    val configManager = ScalafmtDynamicConfigService.instanceIn(file.getProject)
    val configOpt = configManager.configForFile(file, FmtVerbosity.FailSilent, resolveFast = true)
    val (indentDefn, indentCall) = configOpt match {
      case Some(config) => (config.continuationIndentDefnSite, config.continuationIndentCallSite)
      case None => (2, 2)
    }
    val indent = parent match {
      case _: ScParameterClause if newChildIndex != 0 =>
        Indent.getSpaceIndent(indentDefn)
      case _: ScArguments if newChildIndex != 0 =>
        Indent.getSpaceIndent(indentCall)
      case m: ScMatch if m.clauses.nonEmpty =>
        Indent.getSpaceIndent(4)
      case _: ScBlock | _: ScTemplateBody | _: ScMatch | _: ScCaseClauses | _: ScCaseClause =>
        Indent.getSpaceIndent(2)
      case _ if parent.getNode.getElementType == ScalaTokenTypes.kIF =>
        Indent.getSpaceIndent(2)
      case _ =>
        Indent.getNoneIndent
    }
    new ChildAttributes(indent, null)
  }

  override def getSpacing(child1: Block, child2: Block): Spacing = {
    val spacing = ScalaSpacingProcessor.getSpacing(child1.asInstanceOf[ScalaBlock], child2.asInstanceOf[ScalaBlock])
    // printSubBlocksSpacingDebugInfoToConsole(child1, child2, spacing)
    spacing
  }

  override def getSubBlocks: util.List[Block] = {
    if (subBlocks == null) {
      val blocks = getDummyBlocks(this)(node, lastNode)
      subBlocks = blocks
        .asScala
        .filterNot(_.asInstanceOf[ScalaBlock].getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)
        .asJava
      // printSubBlocksDebugInfoToConsole()
    }
    subBlocks
  }

  def isLeaf(node: ASTNode): Boolean = {
    lastNode == null && node.getFirstChildNode == null
  }

  @tailrec
  private def isIncomplete(node: ASTNode): Boolean = {
    if (node.getPsi.isInstanceOf[PsiErrorElement]) {
      true
    } else {
      findLastNonBlankChild(node) match {
        case null => false
        case lastChild: ASTNode =>
          isIncomplete(lastChild)
      }
    }
  }

  private def findLastNonBlankChild(node: ASTNode): ASTNode = {
    var lastChild = node.getLastChildNode
    while (lastChild != null && isBlank(lastChild.getPsi)) {
      lastChild = lastChild.getTreePrev
    }
    lastChild
  }

  @inline
  private def isBlank(psi: PsiElement): Boolean = psi.isInstanceOf[PsiWhiteSpace] || psi.isInstanceOf[PsiComment]

  private var _suggestedWrap: Wrap = _

  def suggestedWrap: Wrap = {
    if (_suggestedWrap == null) {
      val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
      _suggestedWrap = ScalaWrapManager.suggestedWrap(this, scalaSettings)
    }
    _suggestedWrap
  }

  def getChildBlockLastNode(childNode: ASTNode): ASTNode =
    (for {
      context <- subBlocksContext
      childContext <- context.childrenAdditionalContexts.get(childNode)
    } yield childContext.lastNode(childNode)).orNull


  def getCustomAlignment(childNode: ASTNode): Option[Alignment] =
    for {
      context <- subBlocksContext
      childContext <- context.childrenAdditionalContexts.get(childNode)
      a <- childContext.alignment
    } yield a


  //noinspection HardCodedStringLiteral
  // use these methods only for debugging
  private def printSubBlocksDebugInfoToConsole(): Unit = {
    println("#########################################")
    println(s"Parent: ${node.getPsi.getClass.getSimpleName} $getTextRange $indent $alignment")
    println(this.debugText)
    println(s"Children: (${if (subBlocks.isEmpty) "<empty>" else subBlocks.size()})")
    subBlocks.asScala.map(_.asInstanceOf[ScalaBlock]).zipWithIndex.foreach { case (child, idx) =>
      println(s"$idx: ${child.debugText}")
      println(s"$idx: ${child.getTextRange} ${child.indent} ${child.alignment} ${child.wrap}")
    }
    println()
  }

  //noinspection HardCodedStringLiteral
  private def printSubBlocksSpacingDebugInfoToConsole(child1: Block, child2: Block, spacing: Spacing): Unit = {
    (child1, child2, spacing) match {
      case (c1: ScalaBlock, c2: ScalaBlock, s: SpacingImpl) =>
        println(
          s"""Spacing:
             |    child1: ${c1.debugText}
             |    child2: ${c2.debugText}
             |    result: $s (${if(s.isReadOnly) "readonly" else ""})
             |""".stripMargin
        )
      case _ =>
    }
  }

  //noinspection HardCodedStringLiteral
  private def debugText: String = {
    import extensions._
    val text = node.getPsi.getContainingFile.getText.substring(getTextRange)
    if (text.trim.length != text.length) s"`$text`"
    else text
  }
}

object ScalaBlock {
  def isConstructorArgOrMemberFunctionParameter(paramClause: ScParameterClause): Boolean = {
    paramClause.owner match {
      case _: ScPrimaryConstructor | _: ScFunction => true
      case _ => false
    }
  }
}

private[formatting]
class SubBlocksContext(val additionalNodes: Seq[ASTNode] = Seq(),
                       val alignment: Option[Alignment] = None,
                       val childrenAdditionalContexts: Map[ASTNode, SubBlocksContext] = Map()) {
  def lastNode(firstNode: ASTNode): ASTNode =
    lastNode.filter(_ != firstNode).orNull

  private def lastNode: Option[ASTNode] = {
    val nodes1 = childrenAdditionalContexts.map { case (_, context) => context.lastNode }.collect { case Some(x) => x }
    val nodes2 = childrenAdditionalContexts.map { case (child, _) => child }
    val nodes = nodes1 ++ nodes2 ++ additionalNodes
    if (nodes.nonEmpty) {
      Some(nodes.maxBy(_.getTextRange.getEndOffset))
    } else {
      None
    }
  }
}

private[formatting]
object SubBlocksContext {
  def apply(node: ASTNode,
            childNodes: Seq[ASTNode],
            childAlignment: Option[Alignment] = None,
            childrenAdditionalContexts: Map[ASTNode, SubBlocksContext] = Map()): SubBlocksContext = {
    new SubBlocksContext(
      additionalNodes = Seq(),
      alignment = None,
      childrenAdditionalContexts = Map(
        node -> new SubBlocksContext(childNodes, childAlignment, childrenAdditionalContexts)
      )
    )
  }

  def apply(childNodesAlignment: Map[ASTNode, Alignment]): SubBlocksContext = {
    new SubBlocksContext(
      additionalNodes = Seq(),
      alignment = None,
      childrenAdditionalContexts = childNodesAlignment
        .view
        .mapValues(a => new SubBlocksContext(Seq(), Some(a), Map()))
        .toMap
    )
  }
}
