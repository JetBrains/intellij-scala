package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.{CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.{isConstructorArgOrMemberFunctionParameter, shouldIndentAfterCaseClause}
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtIndents
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScExtension, ScExtensionBody, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

import java.util
import scala.annotation.{tailrec, unused}
import scala.jdk.CollectionConverters._

/**
 * @param indent using `var` for `indent` because it's much easier this way when handling method call chains
 *               (see [[ChainedMethodCallsBlockBuilder]]
 */
class ScalaBlock(
  val parentBlock: Option[ScalaBlock],
  val node: ASTNode,
  @Nullable val lastNode: ASTNode,
  @Nullable val alignment: Alignment,
  @Nullable var indent: Indent,
  @Nullable val wrap: Wrap,
  val settings: CodeStyleSettings,
  val subBlocksContext: Option[SubBlocksContext]
) extends ASTBlock with ScalaTokenTypes {

  def this(
    node: ASTNode,
    lastNode: ASTNode,
    @Nullable alignment: Alignment,
    @Nullable indent: Indent,
    @Nullable wrap: Wrap,
    settings: CodeStyleSettings,
    subBlocksContext: Option[SubBlocksContext] = None
  ) = {
    this(None, node, lastNode, alignment, indent, wrap, settings, subBlocksContext)
  }

  protected var subBlocks: util.List[Block] = _

  def commonSettings: CommonCodeStyleSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE)

  override def getNode: ASTNode = node

  override def getTextRange: TextRange =
    if (lastNode == null) node.getTextRange
    else new TextRange(node.getTextRange.getStartOffset, lastNode.getTextRange.getEndOffset)

  @Nullable override def getIndent: Indent = indent

  @Nullable override def getWrap: Wrap = wrap

  @Nullable override def getAlignment: Alignment = alignment

  override def isLeaf: Boolean = isLeaf(node)

  override def isIncomplete: Boolean = ScalaBlock.isIncomplete(if (lastNode != null) lastNode else node)

  override def getChildAttributes(newChildIndex: Int): ChildAttributes = {
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    if (scalaSettings.USE_SCALAFMT_FORMATTER)
      getChildAttributesScalafmtInner(newChildIndex)
    else
      getChildAttributesIntellijInner(newChildIndex, scalaSettings)
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
      case l: ScStringLiteral if l.isMultiLineString && scalaSettings.supportMultilineString =>
        new ChildAttributes(Indent.getSpaceIndent(3, true), null)
      case b: ScBlockExpr if b.resultExpression.exists(_.is[ScFunctionExpr]) || b.caseClauses.isDefined =>
        val indent = {
          val nodeBeforeLast = b.resultExpression.orElse(b.caseClauses).get.getNode.getTreePrev
          val isLineBreak = nodeBeforeLast.getElementType == TokenType.WHITE_SPACE && nodeBeforeLast.textContains('\n')
          val extraIndent =
            if (b.isEnclosedByBraces && isLineBreak && getSubBlocks().size - 1 == newChildIndex) 1
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
      case ElementType(ScalaTokenTypes.tCOLON) if blockFirstNode.getParent.is[ScPackaging] =>
        // The ScalaBlock of a packaging block starts with a ':' token

        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScBlock =>
        val indent = blockFirstNode.getParent match {
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
      case (_: ScExtendsBlock) & Parent(_: ScGivenDefinition) =>
        new ChildAttributes(Indent.getNormalIndent, this.getAlignment)
      // given intOrd: Ord[Int] with <caret+Enter> (top level definition, as a last element in file)
      // in this case `com.intellij.formatting.FormatProcessor.getParentFor` doesn't select ScExtendsBlock
      case (_: ScTemplateParents) & Parent((_: ScExtendsBlock) & Parent(_: ScGivenDefinition)) if lastNode.getElementType == ScalaTokenTypes.kWITH =>
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

  /**
   * Used to pass to intellij logic when scalafmt is enabled.<br>
   * In case there are any changes in IntelliJ formatter settings (even though scalafmt is selected),
   * we do not want these settings to be applicable in `getChildAttributesScalafmtInner`
   */
  private val DefaultScalaCodeStyleSettings = new ScalaCodeStyleSettings(settings)

  // TODO: in latest scalafmt versions there are a lot of new more-precise indent values.
  //  We should handle all of them to provide proper indent on Enter handler
  //  see https://scalameta.org/scalafmt/docs/configuration.html#indentation
  //  indent.main (handled)
  //  indent.callSite (handled)
  //  indent.defnSite (handled)
  //  indent.significant (asked to remove it https://github.com/scalameta/scalafmt/issues)
  //  indent.ctrlSite
  //  indent.ctorSite
  //  indent.caseSite
  //  indent.extendSite
  //  indent.withSiteRelativeToExtends
  //  indent.commaSiteRelativeToExtends
  //  indent.extraBeforeOpenParenDefnSite
  //  indentOperator
  private def getChildAttributesScalafmtInner(newChildIndex: Int): ChildAttributes = {
    val blockFirstNode = getNode.getPsi

    val file = blockFirstNode.getContainingFile
    val configManager = ScalafmtDynamicConfigService.instanceIn(file.getProject)
    val configOpt = configManager.configForFile(file, FmtVerbosity.FailSilent, resolveFast = true)
    val scalafmtIndents = configOpt.map(ScalafmtIndents.apply).getOrElse(ScalafmtIndents.Default)

    val scalamtSpecificIndentOpt = blockFirstNode match {
      case _: ScParameterClause if newChildIndex != 0 => Some(Indent.getSpaceIndent(scalafmtIndents.defnSite))
      case _: ScArguments if newChildIndex != 0       => Some(Indent.getSpaceIndent(scalafmtIndents.callSite))
      case _                                          => None
    }

    val indent = scalamtSpecificIndentOpt.getOrElse {
      //fallback to default intellij indent calculation logic
      val intellijChildAttributes = getChildAttributesIntellijInner(newChildIndex, DefaultScalaCodeStyleSettings)
      val intellijIndent = intellijChildAttributes.getChildIndent
      val useScalafmtMainIndent = intellijIndent.getType match {
        case Indent.Type.SPACES => false
        case Indent.Type.NONE   => false
        case _                  => true
      }
      if (useScalafmtMainIndent)
        Indent.getSpaceIndent(scalafmtIndents.main)
      else
        intellijIndent
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
      subBlocks = new ScalaBlockBuilder(this).buildSubBlocks
      subBlocks.removeIf(_.asInstanceOf[ScalaBlock].getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)
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
      _suggestedWrap = ScalaWrapManager.suggestedWrap(this)
    }
    _suggestedWrap
  }

  def getChildBlockContext(childNode: ASTNode): Option[SubBlocksContext] =
    for {
      context <- subBlocksContext
      childContext <- context.childrenAdditionalContexts.get(childNode)
    } yield childContext

  def getChildBlockLastNode(childNode: ASTNode): ASTNode = {
    val childContext = getChildBlockContext(childNode)
    childContext.map(_.lastNode(childNode)).orNull
  }

  def getChildBlockCustomAlignment(childNode: ASTNode): Option[Alignment] = {
    val childContext = getChildBlockContext(childNode)
    childContext.flatMap(_.alignment)
  }


  //noinspection HardCodedStringLiteral
  // use these methods only for debugging
  @unused("debug print utility")
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
  @unused("debug print utility")
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
    import org.jetbrains.plugins.scala.extensions._
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
    if (psi.is[PsiErrorElement])
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
      case _: ScFunctionExpr =>
        // call: arg => <caret>
        val elementType = lastChild.getElementType
        elementType == ScalaTokenTypes.tFUNTYPE || elementType == ScalaTokenType.ImplicitFunctionArrow
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

  @Nullable
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




