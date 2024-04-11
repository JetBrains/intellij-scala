package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.{Block, ChildAttributes, Indent}
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, TokenType}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.isConstructorArgOrMemberFunctionParameter
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtIndents
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScFunctionalTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlock, ScBlockExpr, ScCatchBlock, ScDo, ScEnumerator, ScExpression, ScFor, ScFunctionExpr, ScIf, ScMatch, ScMethodCall, ScWhile}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScArguments, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDefinitionWithAssignment, ScExtension, ScExtensionBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import java.util

//TODO: enter here should indent caret:
//import scala.util.{
//  chaining,
//  Random,<caret>
//}
/**
 * Contains extracted logic of [[ScalaBlock.getChildAttributes]]
 *
 * See [[com.intellij.formatting.Block.getChildAttributes]]
 */
private object ScalaBlockChildAttributes {

  def getChildAttributes(block: ScalaBlock, newChildIndex: Int): ChildAttributes = {
    val scalaSettings = block.settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    if (scalaSettings.USE_SCALAFMT_FORMATTER)
      getChildAttributesScalafmtInner(block, newChildIndex)
    else
      getChildAttributesIntellijInner(block, newChildIndex, scalaSettings)
  }

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
  private def getChildAttributesScalafmtInner(block: ScalaBlock, newChildIndex: Int): ChildAttributes = {
    val blockFirstNode = block.getNode.getPsi

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
      val intellijChildAttributes = getChildAttributesIntellijInner(block, newChildIndex, block.DefaultScalaCodeStyleSettings)
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

  private def getChildAttributesIntellijInner(block: ScalaBlock, newChildIndex: Int, scalaSettings: ScalaCodeStyleSettings): ChildAttributes = {
    val blockFirstNode = block.getNode.getPsi

    val indentSize = block.settings.getIndentSize(ScalaFileType.INSTANCE)
    val braceShifted = block.settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED

    object ElementType {
      def unapply(psi: PsiElement): Some[IElementType] =
        Some(psi.getNode.getElementType)
    }

    blockFirstNode match {
      case m: ScMatch =>
        val isAfterLastCaseClause = m.clauses.nonEmpty
        val indent =
          if (isAfterLastCaseClause)
            if (shouldIndentAfterCaseClause(newChildIndex, block.getSubBlocks))
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
        val indent = if (shouldIndentAfterCaseClause(newChildIndex, block.getSubBlocks)) Indent.getNormalIndent else Indent.getNoneIndent
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
            if (b.isEnclosedByBraces && isLineBreak && block.getSubBlocks().size - 1 == newChildIndex) 1
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
      case scope if isBlockOnlyScope(block, scope) =>
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
        new ChildAttributes(Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE), block.getAlignment)
      case x: ScDo =>
        val indent =
          if (x.body.isDefined) Indent.getNoneIndent
          else if (block.settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED) Indent.getNoneIndent
          else Indent.getNormalIndent
        new ChildAttributes(indent, null)
      case _: ScXmlElement =>
        new ChildAttributes(Indent.getNormalIndent, null)
      case _: ScalaFile =>
        new ChildAttributes(Indent.getNoneIndent, null)
      case _: ScMethodCall if newChildIndex > 0 =>
        val prevChildBlock = block.getSubBlocks.get(newChildIndex - 1)
        new ChildAttributes(prevChildBlock.getIndent, prevChildBlock.getAlignment)
      case _: ScExpression | _: ScPattern | _: ScParameters =>
        new ChildAttributes(Indent.getContinuationWithoutFirstIndent, block.getAlignment)
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
        new ChildAttributes(indent, block.getAlignment)
      case _: ScArgumentExprList =>
        new ChildAttributes(Indent.getNormalIndent, block.getAlignment)
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
        new ChildAttributes(Indent.getNormalIndent, block.getAlignment)
      // given intOrd: Ord[Int] with <caret+Enter> (top level definition, as a last element in file)
      // in this case `com.intellij.formatting.FormatProcessor.getParentFor` doesn't select ScExtendsBlock
      case (_: ScTemplateParents) & Parent((_: ScExtendsBlock) & Parent(_: ScGivenDefinition)) if block.lastNode.getElementType == ScalaTokenTypes.kWITH =>
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

  private def isBlockOnlyScope(block: ScalaBlock, scope: PsiElement): Boolean = {
    !block.isLeaf && ScalaTokenTypes.LBRACE_LPARENT_TOKEN_SET.contains(scope.getNode.getElementType) &&
      (scope.getParent match {
        case _: ScFor | _: ScPackaging => true
        case _ => false
      })
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
