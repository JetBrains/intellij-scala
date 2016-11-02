package org.jetbrains.plugins.scala
package lang
package formatting
package processors

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

object ScalaIndentProcessor extends ScalaTokenTypes {
  def getChildIndent(parent: ScalaBlock, child: ASTNode): Indent = {
    val settings = parent.getCommonSettings
    val scalaSettings: ScalaCodeStyleSettings = parent.getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val node = parent.getNode
    if (child.getElementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS ||
                child.getElementType == ScalaDocTokenType.DOC_COMMENT_END) {
      return Indent.getSpaceIndent(if (scalaSettings.USE_SCALADOC2_FORMATTING) 2 else 1)
    }
    if ((node.getElementType == ScalaTokenTypes.kIF || node.getElementType == ScalaTokenTypes.kELSE) &&
         parent.myLastNode != null) {
      child.getPsi match {
        case _: ScBlockExpr if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
            settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 =>
          return Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE)
        case _: ScBlockExpr => return Indent.getSpaceIndent(0, scalaSettings.ALIGN_IF_ELSE)
        case _: ScExpression => return Indent.getNormalIndent(scalaSettings.ALIGN_IF_ELSE)
        case _ => return Indent.getSpaceIndent(0, scalaSettings.ALIGN_IF_ELSE)
      }
    }
    if (node.getElementType == ScalaTokenTypes.kYIELD && child.getElementType != ScalaTokenTypes.kYIELD) {
      return Indent.getNormalIndent
    }

    def processFunExpr(expr: ScFunctionExpr): Indent = expr.result match {
      case Some(e) if e == child.getPsi =>
        child.getPsi match {
          case _: ScBlockImpl => Indent.getNoneIndent
          case _: ScBlockExpr if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
            settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
          case _: ScBlockExpr => Indent.getNoneIndent
          case _: ScExpression => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      case Some(_) if child.isInstanceOf[PsiComment] => Indent.getNormalIndent
      //the above case is a hack added to fix SCL-6803; probably will backfire with unintended indents
      case _ => Indent.getNoneIndent
    }


    def processMethodCall = child.getPsi match {
      case arg: ScArgumentExprList if arg.isBraceArgs =>
        if (settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
          settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2) Indent.getNormalIndent
        else Indent.getNoneIndent
      case _ => Indent.getContinuationWithoutFirstIndent
    }

    //the methodCall/functionExpr have dot block as optional, so cases with and without dot are considered
    if (node.getElementType == ScalaTokenTypes.tDOT)
      Option(node.getTreeParent).map(_.getTreeParent).filter(_ != null).map {
      _.getPsi match {
        case expr: ScFunctionExpr =>
          return processFunExpr(expr)
        case _: ScMethodCall =>
          return processMethodCall
        case _ if node.getTreeParent.getPsi.isInstanceOf[ScReferenceExpression] =>
          //proper indentation for chained ref exprs
          return Indent.getContinuationWithoutFirstIndent
        case _ =>
      }
    }

    if (node.getElementType == ScalaTokenTypes.tLBRACE &&
      Option(node.getTreeParent).map(_.getElementType).
        exists(Set[IElementType](ScalaElementTypes.TRY_BLOCK, ScalaElementTypes.PACKAGING).contains)) {
      return if (child.getElementType == ScalaTokenTypes.tLBRACE ||
        child.getElementType == ScalaTokenTypes.tRBRACE) Indent.getNoneIndent
      else if (settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED) Indent.getNoneIndent
      else Indent.getNormalIndent()
    }

    //TODO these are hack methods to facliltate indenting in cases when comment before def/val/var adds one more level of blocks
    def funIndent = child.getPsi match {
      case _: ScBlockExpr if settings.METHOD_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
        settings.METHOD_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
      case _: ScBlockExpr => Indent.getNoneIndent
      case _: ScExpression => Indent.getNormalIndent
      case _: ScParameters if scalaSettings.INDENT_FIRST_PARAMETER_CLAUSE => Indent.getContinuationIndent
      case _ => Indent.getNoneIndent
    }
    def valIndent = child.getPsi match {
      case _: ScBlockExpr if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
        settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
      case _: ScBlockExpr => Indent.getNoneIndent
      case _: ScExpression | _: ScTypeElement => Indent.getNormalIndent
      case _ => Indent.getNoneIndent
    }

    import ScalaElementTypes._
    import ScalaTokenTypes._

    node.getPsi match {
      case expr: ScFunctionExpr => processFunExpr(expr)
      case _: ScXmlElement =>
        child.getPsi match {
          case _: ScXmlStartTag | _: ScXmlEndTag | _: ScXmlEmptyTag => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScalaFile => Indent.getNoneIndent
      case p: ScPackaging =>
        if (p.isExplicit) child.getElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tRBRACE =>
            if (settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
              settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2) Indent.getNormalIndent
            else
              Indent.getNoneIndent
          case _ => Indent.getNoneIndent
        } else Indent.getNoneIndent
      case _: ScMatchStmt =>
        child.getPsi match {
          case _: ScCaseClauses if settings.INDENT_CASE_FROM_SWITCH => Indent.getNormalIndent
          case _: PsiComment => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      case _: ScTryBlock =>
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.tRBRACE if parent.myLastNode == null =>
            //getting indent for braces from tryBlock
            if (settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
              settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2) Indent.getNormalIndent else
              Indent.getNoneIndent
          case ScalaTokenTypes.kTRY => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScCatchBlock =>
        child.getElementType match {
          case ScalaTokenTypes.kCATCH =>
            Indent.getNoneIndent
          case _ if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScEarlyDefinitions | _: ScTemplateBody =>
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE |
                  ScalaTokenTypes.tRBRACE =>
            Indent.getNoneIndent
          case _ if settings.CLASS_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case b: ScBlockExpr if b.getParent.isInstanceOf[ScFunction] =>
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE |
                  ScalaTokenTypes.tRBRACE =>
            Indent.getNoneIndent
          case _ if settings.METHOD_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScRefinement | _: ScExistentialClause | _: ScBlockExpr  =>
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE |
                  ScalaTokenTypes.tRBRACE =>
            Indent.getNoneIndent
          case _ if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScTryStmt => Indent.getNoneIndent
      case _: ScFunction => funIndent
      case _ if node.getElementType == ScalaTokenTypes.kDEF ||
        Option(node.getTreeParent).exists(p => Set[IElementType](FUNCTION_DECLARATION, FUNCTION_DEFINITION).
          contains(p.getElementType)) && node.getElementType == MODIFIERS => funIndent
      case _: ScMethodCall => processMethodCall
      case arg: ScArgumentExprList if arg.isBraceArgs =>
        if (scalaSettings.INDENT_BRACED_FUNCTION_ARGS &&
          arg.children.exists(child => Set(ScalaTokenTypes.tLPARENTHESIS, ScalaTokenTypes.tRPARENTHESIS).contains(child.getNode.getElementType)) &&
          child.getElementType != ScalaTokenTypes.tRPARENTHESIS &&
          child.getElementType != ScalaTokenTypes.tLPARENTHESIS) Indent.getNormalIndent
        else Indent.getNoneIndent
      case _: ScIfStmt | _: ScWhileStmt | _: ScDoStmt | _: ScForStatement | _: ScFinallyBlock | _: ScCatchBlock |
           _: ScValue | _: ScVariable | _: ScTypeAlias =>
        if (child.getElementType == ScalaTokenTypes.kYIELD) Indent.getNormalIndent
        else valIndent
      case _ if node.getElementType == ScalaTokenTypes.kVAL || node.getElementType == ScalaTokenTypes.kVAR ||
        Option(node.getTreeParent).exists(p =>
          Set[IElementType](PATTERN_DEFINITION, VALUE_DECLARATION, VARIABLE_DEFINITION, VARIABLE_DECLARATION).
            contains(p.getElementType)) && node.getElementType == MODIFIERS => valIndent
      case _: ScCaseClause =>
        child.getElementType match {
          case ScalaTokenTypes.kCASE | ScalaTokenTypes.tFUNTYPE => Indent.getNoneIndent
          case _ =>
            child.getPsi match {
              case _: ScBlockImpl => Indent.getNoneIndent
              case _: ScBlockExpr if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
                  settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
              case _: ScBlockExpr => Indent.getNoneIndent
              case _: ScGuard => Indent.getNormalIndent
              case _ => Indent.getNormalIndent
            }
        }
      case block: ScBlockImpl =>
        block.getParent match {
          case _: ScCaseClause | _: ScFunctionExpr =>
            child.getPsi match {
              case _: ScBlockExpr if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
                settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
              case _: ScBlockExpr => Indent.getNoneIndent
              case _ => if (scalaSettings.DO_NOT_INDENT_CASE_CLAUSE_BODY) Indent.getNoneIndent else Indent.getNormalIndent
            }
          case _ => Indent.getNoneIndent
        }
      case _: ScBlock => Indent.getNoneIndent
      case _: ScEnumerators => Indent.getNormalIndent
      case _: ScExtendsBlock if child.getElementType != TEMPLATE_BODY => Indent.getContinuationIndent
      case _: ScExtendsBlock if settings.CLASS_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
        settings.CLASS_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
      case _: ScExtendsBlock => Indent.getNoneIndent //Template body
      case _: ScParameterClause if child.getElementType == tRPARENTHESIS ||
        child.getElementType == tLPARENTHESIS => Indent.getNoneIndent
      case p: ScParameterClause if scalaSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS && isConstructorArgOrMemberFunctionParameter(p) =>
        Indent.getSpaceIndent(scalaSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS, false)
      case p: ScParameterClause if scalaSettings.USE_ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS && isConstructorArgOrMemberFunctionParameter(p) =>
        Indent.getSpaceIndent(scalaSettings.ALTERNATE_CONTINUATION_INDENT_FOR_PARAMS, false)
      case _: ScParameterClause if  scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS =>
        val parent = node.getTreeParent
        if (parent != null && parent.getPsi.isInstanceOf[ScParameters] && parent.getTreeParent != null) {
          if (parent.getTreeParent.getPsi.isInstanceOf[ScFunctionExpr]) {
            return Indent.getNoneIndent
          }
        }
        Indent.getNormalIndent
      case _: ScParenthesisedExpr | _: ScParenthesisedPattern | _: ScParenthesisedExpr =>
        Indent.getContinuationWithoutFirstIndent(settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION)
      case _: ScTuple | _: ScUnitExpr =>
        if (scalaSettings.DO_NOT_INDENT_TUPLES_CLOSE_BRACE &&
          child.getElementType == ScalaTokenTypes.tRPARENTHESIS) Indent.getSpaceIndent(0, scalaSettings.ALIGN_TUPLE_ELEMENTS)
        else Indent.getContinuationWithoutFirstIndent(scalaSettings.ALIGN_TUPLE_ELEMENTS)
      case _: ScParameters | _: ScParameterClause | _: ScPattern | _: ScTemplateParents |
              _: ScExpression | _: ScTypeElement | _: ScTypes | _: ScTypeArgs =>
        Indent.getContinuationWithoutFirstIndent
      case _: ScArgumentExprList =>
        if (child.getElementType != ScalaTokenTypes.tRPARENTHESIS &&
            child.getElementType != ScalaTokenTypes.tLPARENTHESIS)
          Indent.getNormalIndent(settings.ALIGN_MULTILINE_METHOD_BRACKETS)
        else Indent.getNoneIndent
      case _: ScDocComment => Indent.getNoneIndent
      case _ if node.getElementType == ScalaTokenTypes.kEXTENDS && child.getElementType != ScalaTokenTypes.kEXTENDS =>
        Indent.getContinuationIndent() //this is here to not break whatever processing there is before
      case _ => Indent.getNoneIndent
    }
  }

  private def isConstructorArgOrMemberFunctionParameter(paramClause: ScParameterClause): Boolean = {
    val owner = paramClause.owner
    owner != null && (owner.isInstanceOf[ScPrimaryConstructor] || owner.isInstanceOf[ScFunction])
  }
}
