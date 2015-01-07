package org.jetbrains.plugins.scala
package lang
package formatting
package processors

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
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
      val comment = child.getPsi.parentsInFile.findByType(classOf[ScDocComment]).getOrElse {
        throw new RuntimeException("Unable to find parent doc comment")
      }
      return Indent.getSpaceIndent(if (comment.version == 1) 1 else 2)
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

    node.getPsi match {
      case expr: ScFunctionExpr =>
        expr.result match {
          case Some(e) if e == child.getPsi =>
            child.getPsi match {
              case _: ScBlockImpl => Indent.getNoneIndent
              case _: ScBlockExpr if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
                settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
              case _: ScBlockExpr => Indent.getNoneIndent
              case _: ScExpression => Indent.getNormalIndent
              case _ => Indent.getNoneIndent
            }
          case Some(e) if child.isInstanceOf[PsiComment] => Indent.getNormalIndent
          //the above case is a hack added to fix SCL-6803; probably will backfire with unintended indents
          case _ => Indent.getNoneIndent
        }
      case el: ScXmlElement =>
        child.getPsi match {
          case _: ScXmlStartTag | _: ScXmlEndTag | _: ScXmlEmptyTag => Indent.getNoneIndent
          case _ => Indent.getNormalIndent
        }
      case _: ScalaFile => Indent.getNoneIndent
      case _: ScPackaging => Indent.getNoneIndent
      case _: ScMatchStmt =>
        child.getPsi match {
          case _: ScCaseClauses if settings.INDENT_CASE_FROM_SWITCH => Indent.getNormalIndent
          case _: PsiComment => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      case _: ScTryBlock | _: ScCatchBlock =>
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.kCATCH |
                  ScalaTokenTypes.tRBRACE | ScalaTokenTypes.kTRY =>
            Indent.getNoneIndent
          case _ if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED => Indent.getNoneIndent
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
      case _: ScFunction =>
        child.getPsi match {
          case _: ScBlockExpr if settings.METHOD_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
              settings.METHOD_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
          case _: ScBlockExpr => Indent.getNoneIndent
          case _: ScExpression => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
      case _: ScMethodCall =>
        child.getPsi match {
          case arg: ScArgumentExprList if arg.isBraceArgs =>
            if (settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
              settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2) Indent.getNormalIndent
            else Indent.getNoneIndent
          case _ => Indent.getContinuationWithoutFirstIndent
        }
      case arg: ScArgumentExprList if arg.isBraceArgs => Indent.getNoneIndent
      case _: ScIfStmt | _: ScWhileStmt | _: ScDoStmt | _: ScForStatement
              | _: ScFinallyBlock | _: ScCatchBlock | _: ScValue | _: ScVariable=>
        child.getPsi match {
          case _: ScBlockExpr if settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
              settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
          case _: ScBlockExpr => Indent.getNoneIndent
          case _: ScExpression => Indent.getNormalIndent
          case _ => Indent.getNoneIndent
        }
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
              case _ => Indent.getNormalIndent
            }
          case _ => Indent.getNoneIndent
        }
      case _: ScBlock => Indent.getNoneIndent
      case _: ScEnumerators => Indent.getNormalIndent
      case _: ScExtendsBlock if child.getElementType != ScalaElementTypes.TEMPLATE_BODY => Indent.getContinuationIndent
      case _: ScExtendsBlock if settings.CLASS_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
        settings.CLASS_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED2 => Indent.getNormalIndent
      case _: ScExtendsBlock => Indent.getNoneIndent //Template body
      case _: ScParameterClause if child.getElementType == ScalaTokenTypes.tRPARENTHESIS ||
                                   child.getElementType == ScalaTokenTypes.tLPARENTHESIS =>
        Indent.getNoneIndent
      case _: ScParameterClause if scalaSettings.NOT_CONTINUATION_INDENT_FOR_PARAMS =>
        val parent = node.getTreeParent
        if (parent != null && parent.getPsi.isInstanceOf[ScParameters] && parent.getTreeParent != null) {
          if (parent.getTreeParent.getPsi.isInstanceOf[ScFunctionExpr]) {
            return Indent.getNoneIndent
          }
        }
        Indent.getNormalIndent
      case _: ScParenthesisedExpr | _: ScParenthesisedPattern | _: ScParenthesisedExpr =>
        Indent.getContinuationWithoutFirstIndent(settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION)
//      case paramClause : ScParameterClause if child.getTreePrev != null && child.getTreePrev.getPsi.isInstanceOf[PsiWhiteSpace] && child.getTreePrev.getText.contains("\n") =>
//        Indent.getContinuationIndent(true)
      case _: ScParameters | _: ScParameterClause | _: ScPattern | _: ScTemplateParents |
              _: ScExpression | _: ScTypeElement | _: ScTypes | _: ScTypeArgs =>
        Indent.getContinuationWithoutFirstIndent
      case _: ScArgumentExprList =>
        val refExpr = node.getTreePrev.getPsi
        if (refExpr.getText.contains("\n")) {
          //ugly hack for SCL-3859
          if (child.getElementType != ScalaTokenTypes.tRPARENTHESIS &&
                  child.getElementType != ScalaTokenTypes.tLPARENTHESIS) {
            val indentSettings = settings.getIndentOptions
            Indent.getSpaceIndent(indentSettings.CONTINUATION_INDENT_SIZE + indentSettings.INDENT_SIZE)
          } else {
            Indent.getContinuationWithoutFirstIndent
          }
        } else if (child.getElementType != ScalaTokenTypes.tRPARENTHESIS &&
            child.getElementType != ScalaTokenTypes.tLPARENTHESIS)
          Indent.getNormalIndent(settings.ALIGN_MULTILINE_METHOD_BRACKETS)
        else Indent.getNoneIndent
      case _: ScDocComment => Indent.getNoneIndent
      case _ => Indent.getNoneIndent
    }
  }
}