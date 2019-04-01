package org.jetbrains.plugins.scala
package lang
package formatting
package processors

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.{NEXT_LINE, NEXT_LINE_SHIFTED, NEXT_LINE_SHIFTED2}
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock.isConstructorArgOrMemberFunctionParameter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

object ScalaIndentProcessor extends ScalaTokenTypes {
  private val ModifiersOrAnnotationOrLineComment = TokenSet.create(
    ScalaElementType.MODIFIERS,
    ScalaElementType.ANNOTATIONS,
    ScalaTokenTypes.tLINE_COMMENT
  )
  private val TryOrPackaging = TokenSet.create(
    ScalaElementType.TRY_BLOCK,
    ScalaElementType.PACKAGING
  )
  private val IfOrElse = TokenSet.create(
    ScalaTokenTypes.kIF,
    ScalaTokenTypes.kELSE
  )

  def getChildIndent(parent: ScalaBlock, child: ASTNode): Indent = {
    val settings = parent.commonSettings
    val scalaSettings = parent.settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

    val node = parent.getNode
    val nodeElementType = node.getElementType
    val nodeTreeParent = node.getTreeParent
    val nodeTreeParentElementType = nodeTreeParent.nullSafe.map(_.getElementType).get
    val childElementType = child.getElementType
    val childPsi = child.getPsi

    val isBraceNextLineShifted1 = settings.BRACE_STYLE == NEXT_LINE_SHIFTED
    val isBraceNextLineShifted2 = settings.BRACE_STYLE == NEXT_LINE_SHIFTED2
    val isBraceNextLineShifted = isBraceNextLineShifted1 || isBraceNextLineShifted2

    def processFunExpr(expr: ScFunctionExpr): Indent = expr.result match {
      case Some(e) if e == childPsi =>
        childPsi match {
          case _: ScBlockImpl => Indent.getNoneIndent
          case _: ScBlockExpr if isBraceNextLineShifted => Indent.getNormalIndent
          case _: ScBlockExpr => Indent.getNoneIndent
          case _: ScExpression => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      case Some(_) if child.isInstanceOf[PsiComment] => Indent.getNormalIndent
      //the above case is a hack added to fix SCL-6803; probably will backfire with unintended indents
      case _ => Indent.getNoneIndent
    }

    def processMethodCall = childPsi match {
      case arg: ScArgumentExprList if arg.isBraceArgs =>
        if (isBraceNextLineShifted) Indent.getNormalIndent
        else Indent.getNoneIndent
      case _ => Indent.getContinuationWithoutFirstIndent
    }

    //TODO these are hack methods to facilitate indenting in cases when comment before def/val/var adds one more level of blocks
    def funIndent = childPsi match {
      case _: ScBlockExpr if settings.METHOD_BRACE_STYLE == NEXT_LINE_SHIFTED || settings.METHOD_BRACE_STYLE == NEXT_LINE_SHIFTED2 =>
        Indent.getNormalIndent
      case _: ScBlockExpr => Indent.getNoneIndent
      case _: ScExpression => Indent.getNormalIndent
      case _: ScParameters if scalaSettings.INDENT_FIRST_PARAMETER_CLAUSE => Indent.getContinuationIndent
      case _ => Indent.getNoneIndent
    }
    def valIndent = childPsi match {
      case _: ScBlockExpr if isBraceNextLineShifted => Indent.getNormalIndent
      case _: ScBlockExpr => Indent.getNoneIndent
      case _: ScExpression | _: ScTypeElement => Indent.getNormalIndent
      case _ => Indent.getNoneIndent
    }

    if (childElementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS ||
      childElementType == ScalaDocTokenType.DOC_COMMENT_END) {
      return Indent.getSpaceIndent(if (scalaSettings.USE_SCALADOC2_FORMATTING) 2 else 1)
    }
    if (IfOrElse.contains(nodeElementType) && parent.lastNode != null) {
      return childPsi match {
        case _: ScBlockExpr if isBraceNextLineShifted => Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE)
        case _: ScBlockExpr => Indent.getSpaceIndent(0, scalaSettings.ALIGN_IF_ELSE)
        case _: ScExpression => Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE)
        case _ => Indent.getSpaceIndent(0, scalaSettings.ALIGN_IF_ELSE)
      }
    }
    if (nodeElementType == ScalaTokenTypes.kYIELD && childElementType != ScalaTokenTypes.kYIELD) {
      return Indent.getNormalIndent
    }

    //the methodCall/functionExpr have dot block as optional, so cases with and without dot are considered
    if (nodeElementType == ScalaTokenTypes.tDOT) {
      val nodeTreeParentParent = nodeTreeParent.nullSafe.map(_.getTreeParent).map(_.getPsi).get
      nodeTreeParentParent match {
        case expr: ScFunctionExpr =>
          return processFunExpr(expr)
        case _: ScMethodCall =>
          return processMethodCall
        case _ if nodeTreeParent.getPsi.isInstanceOf[ScReferenceExpression] =>
          //proper indentation for chained ref exprs
          return Indent.getContinuationWithoutFirstIndent
        case _ =>
      }
    }

    if (nodeElementType == ScalaTokenTypes.tLBRACE && TryOrPackaging.contains(nodeTreeParentElementType)) {
      return if (ScalaTokenTypes.BRACES_TOKEN_SET.contains(childElementType)) Indent.getNoneIndent
      else if (isBraceNextLineShifted1) Indent.getNoneIndent
      else Indent.getNormalIndent
    }

    node.getPsi match {
      case expr: ScFunctionExpr => processFunExpr(expr)
      case _: ScXmlElement =>
        childPsi match {
          case _: ScXmlStartTag | _: ScXmlEndTag | _: ScXmlEmptyTag => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScalaFile => Indent.getNoneIndent
      case p: ScPackaging =>
        childElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tRBRACE if p.isExplicit && isBraceNextLineShifted =>
            Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      case _: ScMatch =>
        childPsi match {
          case _: ScCaseClauses if settings.INDENT_CASE_FROM_SWITCH => Indent.getNormalIndent
          case _: PsiComment => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      case _: ScTryBlock =>
        childElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tRBRACE if parent.lastNode == null =>
            //getting indent for braces from tryBlock
            if (isBraceNextLineShifted) Indent.getNormalIndent
            else Indent.getNoneIndent
          case ScalaTokenTypes.kTRY => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScCatchBlock =>
        childElementType match {
          case ScalaTokenTypes.kCATCH => Indent.getNoneIndent
          case _ if settings.BRACE_STYLE == NEXT_LINE => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScEarlyDefinitions | _: ScTemplateBody =>
        childElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tRBRACE => Indent.getNoneIndent
          case _ if settings.CLASS_BRACE_STYLE == NEXT_LINE_SHIFTED => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case b: ScBlockExpr if b.getParent.isInstanceOf[ScFunction] =>
        childElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tRBRACE => Indent.getNoneIndent
          case _ if settings.METHOD_BRACE_STYLE == NEXT_LINE_SHIFTED => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScRefinement | _: ScExistentialClause | _: ScBlockExpr  =>
        childElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tRBRACE => Indent.getNoneIndent
          case _ if isBraceNextLineShifted1 => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScTry => Indent.getNoneIndent
      case _: ScFunction => funIndent
      case _ if nodeElementType == ScalaTokenTypes.kDEF ||
        TokenSets.FUNCTIONS.contains(nodeTreeParentElementType) &&
          ModifiersOrAnnotationOrLineComment.contains(nodeElementType) =>
        funIndent
      case _: ScMethodCall => processMethodCall
      case arg: ScArgumentExprList if arg.isBraceArgs =>
        if (scalaSettings.INDENT_BRACED_FUNCTION_ARGS &&
          arg.children.exists(c => ScalaTokenTypes.PARENTHESIS_TOKEN_SET.contains(c.elementType)) &&
          !ScalaTokenTypes.PARENTHESIS_TOKEN_SET.contains(childElementType)
        ) {
          Indent.getNormalIndent
        } else {
          Indent.getNoneIndent
        }
      case _: ScIf | _: ScWhile | _: ScDo | _: ScFor | _: ScFinallyBlock | _: ScCatchBlock |
           _: ScValue | _: ScVariable | _: ScTypeAlias =>
        if (childElementType == ScalaTokenTypes.kYIELD) Indent.getNormalIndent
        else valIndent
      case _ if ScalaTokenTypes.VAL_VAR_TOKEN_SET.contains(nodeElementType) ||
        TokenSets.PROPERTIES.contains(nodeTreeParentElementType) && nodeElementType == ScalaElementType.MODIFIERS =>
        valIndent
      case _: ScCaseClause =>
        childElementType match {
          case ScalaTokenTypes.kCASE | ScalaTokenTypes.tFUNTYPE => Indent.getNoneIndent
          case _ =>
            childPsi match {
              case _: ScBlockImpl => Indent.getNoneIndent
              case _: ScBlockExpr if isBraceNextLineShifted => Indent.getNormalIndent
              case _: ScBlockExpr => Indent.getNoneIndent
              case _: ScGuard => Indent.getNormalIndent
              case _ => Indent.getNormalIndent
            }
        }
      case block: ScBlockImpl =>
        block.getParent match {
          case _: ScCaseClause | _: ScFunctionExpr =>
            childPsi match {
              case _: ScBlockExpr if isBraceNextLineShifted => Indent.getNormalIndent
              case _: ScBlockExpr => Indent.getNoneIndent
              case _ if scalaSettings.DO_NOT_INDENT_CASE_CLAUSE_BODY => Indent.getNoneIndent
              case _ => Indent.getNormalIndent
            }
          case _ => Indent.getNoneIndent
        }
      case _: ScBlock => Indent.getNoneIndent
      case enum: ScEnumerators => if (enum.getPrevSibling != null) Indent.getNoneIndent else Indent.getNormalIndent
      case _: ScExtendsBlock if childElementType != ScalaElementType.TEMPLATE_BODY => Indent.getContinuationIndent
      case _: ScExtendsBlock if settings.CLASS_BRACE_STYLE == NEXT_LINE_SHIFTED || settings.CLASS_BRACE_STYLE == NEXT_LINE_SHIFTED2 =>
        Indent.getNormalIndent
      case _: ScExtendsBlock => Indent.getNoneIndent //Template body
      case _: ScParameterClause if ScalaTokenTypes.PARENTHESIS_TOKEN_SET.contains(childElementType) =>
        Indent.getNoneIndent
      case p: ScParameterClause if scalaSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS && isConstructorArgOrMemberFunctionParameter(p) =>
        Indent.getSpaceIndent(scalaSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS, false)
      case _: ScParameterClause if  scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS =>
        val nodeTreeParentPsi = nodeTreeParent.nullSafe.map(_.getPsi).get
        val nodeTreeParentParentPsi = nodeTreeParent.nullSafe.map(_.getTreeParent).map(_.getPsi).get
        (nodeTreeParentPsi, nodeTreeParentParentPsi) match {
          case (_: ScParameters, _: ScFunctionExpr) => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScParenthesisedExpr | _: ScParenthesisedPattern | _: ScParenthesisedExpr =>
        Indent.getContinuationWithoutFirstIndent(settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION)
      case _: ScTuple | _: ScUnitExpr =>
        if (scalaSettings.DO_NOT_INDENT_TUPLES_CLOSE_BRACE && childElementType == ScalaTokenTypes.tRPARENTHESIS) {
          Indent.getSpaceIndent(0, scalaSettings.ALIGN_TUPLE_ELEMENTS)
        } else {
          Indent.getContinuationWithoutFirstIndent(scalaSettings.ALIGN_TUPLE_ELEMENTS)
        }
      case _: ScParameters | _: ScParameterClause | _: ScPattern | _: ScTemplateParents |
              _: ScExpression | _: ScTypeElement | _: ScTypes | _: ScTypeArgs | _:  ScGenerator =>
        Indent.getContinuationWithoutFirstIndent
      case _: ScArgumentExprList =>
        if (ScalaTokenTypes.PARENTHESIS_TOKEN_SET.contains(childElementType)) Indent.getNoneIndent
        else Indent.getNormalIndent(false)
      case _: ScDocComment => Indent.getNoneIndent
      case _ if nodeElementType == ScalaTokenTypes.kEXTENDS && childElementType != ScalaTokenTypes.kEXTENDS =>
        Indent.getContinuationIndent() //this is here to not break whatever processing there is before
      case leaf: LeafPsiElement if (
        nodeElementType == ScalaTokenTypes.tLBRACE && childElementType != ScalaTokenTypes.tRBRACE ||
        nodeElementType == ScalaTokenTypes.tLPARENTHESIS && childElementType != ScalaTokenTypes.tRPARENTHESIS
        ) && leaf.getNextSiblingNotWhitespaceComment.isInstanceOf[ScEnumerators]  =>
        Indent.getNormalIndent
      case _: ScImportSelectors if childElementType != ScalaTokenTypes.tRBRACE &&
        childElementType != ScalaTokenTypes.tLBRACE => Indent.getNormalIndent
      case Parent(p) if p.isInstanceOf[ScReferenceExpression] && childElementType == ScalaElementType.TYPE_ARGS=>
        Indent.getContinuationWithoutFirstIndent
      case _ =>
        Indent.getNoneIndent
    }
  }

}