package org.jetbrains.plugins.scala
package lang
package formatting

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.{isConstructorArgOrMemberFunctionParameter, shouldIndentAfterCaseClause}
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScExtension, ScExtensionBody, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import java.util
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

  override def isIncomplete: Boolean = ScalaBlock.isIncomplete(node)

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
    val blockFirstNode = getNode.getPsi

    val indentSize = settings.getIndentSize(ScalaFileType.INSTANCE)
    val braceShifted = settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED

    object ElementType {
      def unapply(psi: PsiElement): Some[IElementType] =
        Some(psi.getNode.getElementType)
    }

    blockFirstNode match {
      case m: ScMatch =>
        val isAfterLastCaseClause = m.clauses.nonEmpty
        val indent =
          if (isAfterLastCaseClause)
            if (shouldIndentAfterCaseClause(newChildIndex, this.subBlocks))
              Indent.getSpaceIndent(2 * indentSize)
            else
              Indent.getNormalIndent // we still need to indent to the `case`
          else if (braceShifted) Indent.getNoneIndent
          else Indent.getNormalIndent
        new ChildAttributes(indent, null)
      case _: ScCaseClauses =>
        // used when Enter / Backspace is pressed after case clause body, in the trailing whitespace
        // note: when the caret is located after the last case clause, this branch is not triggered,
        // because parent of the last whitespace is ScMatch
        val indent = if (shouldIndentAfterCaseClause(newChildIndex, this.subBlocks)) Indent.getNormalIndent else Indent.getNoneIndent
        new ChildAttributes(indent, null)
      case _: ScCaseClause =>
        // used when Enter / Backspace is pressed inside case clause body (not in the trailing space)
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
      case _: ScBlockExpr | _: ScEarlyDefinitions | _: ScTemplateBody | _: ScExtensionBody |
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
        val grandParent = blockFirstNode.getParent
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
      case _: ScArgumentExprList =>
        new ChildAttributes(Indent.getNormalIndent, this.getAlignment)
      case _: ScFunctionalTypeElement =>
        new ChildAttributes(Indent.getNormalIndent, null)
      // def, var, val, type, given + `=`
      case _: ScDefinitionWithAssignment =>
        new ChildAttributes(Indent.getNormalIndent, null)
      // extension (ss: Seq[String]) ...
      case _: ScExtension =>
        new ChildAttributes(Indent.getNormalIndent, null)
      // given intOrd: Ord[Int] with <caret+Enter>
      case (_: ScExtendsBlock) && Parent(_: ScGivenDefinition) =>
        new ChildAttributes(Indent.getNormalIndent, this.getAlignment)
      // given intOrd: Ord[Int] with <caret+Enter> (top level definition, as a last element in file)
      // in this case `com.intellij.formatting.FormatProcessor.getParentFor` doesn't select ScExtendsBlock
      case (_: ScTemplateParents) && Parent((_: ScExtendsBlock) && Parent(_: ScGivenDefinition)) if lastNode.getElementType == ScalaTokenTypes.kWITH =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case ElementType(ScalaTokenTypes.kEXTENDS) =>
        if (scalaSettings.ALIGN_EXTENDS_WITH == ScalaCodeStyleSettings.ALIGN_TO_EXTENDS)
          new ChildAttributes(Indent.getNoneIndent, null)
        else
          new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScEnumerator =>
        new ChildAttributes(Indent.getNormalIndent, null)
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

  @tailrec
  final def isIncomplete(node: ASTNode): Boolean = {
    val psi = node.getPsi
    if (psi.isInstanceOf[PsiErrorElement])
      return true

    val lastChild = findLastNonBlankChild(node)
    if (lastChild == null)
      return false // leaf node

    // mostly required for Enter handler
    val isCurrentIncomplete = psi match {
      // `class A:<caret>`
      case _: ScTemplateBody => lastChild.getElementType == ScalaTokenTypes.tCOLON
      // `given intOrd: Ord[Int] with <caret>`
      case _: ScExtendsBlock => lastChild.getElementType == ScalaTokenTypes.kWITH
      case ret: ScReturn if ret.expr.isEmpty =>
        // NOTE: compare only type text, do not
        val hasUnitReturnType: Boolean = ret.method.exists(_.returnTypeElement.exists(isUnitTypeText))
        !hasUnitReturnType
      case _ => false
    }
    if (isCurrentIncomplete)
      true
    else
      isIncomplete(lastChild)
  }

  private def isUnitTypeText(typeElement: ScTypeElement): Boolean = {
    val text = typeElement.getText
    text match {
      case "Unit" | "scala.Unit" | "_root_.scala.Unit" => true
      case _ => false
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
  private def isBlank(psi: PsiElement): Boolean = psi match {
    // empty template, e.g. in empty `given intOrd: Ord[Int] with <caret>`
    case body: ScTemplateBody             => body.getFirstChild == null
    // e.g. empty case clause body
    case block: ScBlock                   => block.getFirstChild == null
    case _: PsiWhiteSpace | _: PsiComment => true
    case _                                => false
  }


  /**
   * When we press enter after caret here: {{{
   *  42 match {
   *    case Pattern1 => doSomething1()<caret>
   *    case _ =>
   *  }
   * }}}
   *
   * We want the caret to be aligned with `case`, not indented<br>
   * (NOTE: the same is for backspace action, performed in reverse)
   */
  private def shouldIndentAfterCaseClause(newChildIndex: Int, subBlocks: util.List[Block]): Boolean =
    if (newChildIndex == 0 || subBlocks.isEmpty)
      false // true
    else {
      val prevCaseClauseBlock = subBlocks.get(newChildIndex - 1) match {
        case b: ScalaBlock => b
        case _ => return false
      }
      val prevCaseClause = prevCaseClauseBlock.getNode.getPsi  match {
        case clause: ScCaseClause => clause
        // for the last case clause, the whitespace belongs to the root `match` node, so previous node is "all the clauses"
        case clauses: ScCaseClauses => clauses.caseClauses.lastOption match {
          case Some(c) => c
          case _ => return false
        }
        case _ => return false
      }
      val hasCodeRightAfterArrow = prevCaseClause.funType.forall(c => !c.followedByNewLine(ignoreComments = false))
      !hasCodeRightAfterArrow
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
