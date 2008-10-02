package org.jetbrains.plugins.scala.lang.formatting.processors

import psi.api.ScalaFile
import psi.api.toplevel.typedef.ScTypeDefinition
import scaladoc.lexer.ScalaDocTokenType
import scaladoc.psi.api.ScDocComment
import settings.ScalaCodeStyleSettings
import com.intellij.formatting._;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._;

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._



object ScalaIndentProcessor extends ScalaTokenTypes {

  def getChildIndent(parent: ScalaBlock, child: ASTNode): Indent = {
    val settings = parent.getSettings
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    val indentCount = scalaSettings.INDENT
    val node = parent.getNode
    node.getPsi match {
      case _: ScalaFile => Indent.getNoneIndent
      case _: ScPackaging => {
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE |
                  ScalaTokenTypes.tRBRACE |
                  ScalaTokenTypes.kPACKAGE |
                  ScalaElementTypes.REFERENCE =>
            Indent.getNoneIndent
          case _ => Indent.getSpaceIndent(indentCount)
        }
      }
      case _: ScMatchStmt => {
        child.getPsi match {
          case _: ScCaseClauses if settings.INDENT_CASE_FROM_SWITCH => Indent.getSpaceIndent(indentCount)
          case _ => Indent.getNoneIndent
        }
      }
      case _: ScTryBlock | _: ScCatchBlock => {
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE | ScalaTokenTypes.kCATCH |
                  ScalaTokenTypes.tRBRACE | ScalaTokenTypes.kTRY => {
            Indent.getNoneIndent
          }
          case _ => Indent.getSpaceIndent(indentCount)
        }
      }
      case _: ScTemplateBody | _: ScRefinement | _: ScExistentialClause | _: ScBlockExpr => {
        child.getElementType match {
          case ScalaTokenTypes.tLBRACE |
                  ScalaTokenTypes.tRBRACE => {
            Indent.getNoneIndent
          }
          case _ => Indent.getSpaceIndent(indentCount)
        }
      }
      case _: ScTryStmt => Indent.getNoneIndent
      case _: ScIfStmt | _: ScWhileStmt | _: ScDoStmt | _: ScForStatement
              | _: ScFinallyBlock | _: ScCatchBlock | _: ScFunction => {
        child.getPsi match {
          case _: ScExpression => Indent.getSpaceIndent(indentCount)
          case _ => Indent.getNoneIndent
        }
      }
      case _: ScCaseClause => {
        child.getElementType match {
          case ScalaTokenTypes.kCASE | ScalaTokenTypes.tFUNTYPE => Indent.getNoneIndent
          case _ =>
            child.getPsi match {
              case _: ScPattern => Indent.getNoneIndent
              case _ => Indent.getSpaceIndent(indentCount)
            }
        }
      }
      case _: ScBlock => Indent.getNoneIndent
      case _: ScEnumerators => Indent.getSpaceIndent(indentCount)
      case _: ScExtendsBlock if child.getElementType != ScalaElementTypes.TEMPLATE_BODY => {
        Indent.getContinuationWithoutFirstIndent
      }
      case _: ScParameters | _: ScParameterClause | _: ScPattern | _: ScTemplateParents |
              _: ScExpression | _: ScTypeElement | _: ScTypes | _: ScAnnotations | _: ScTypeArgs => {
        Indent.getContinuationWithoutFirstIndent
      }
      case _: ScArgumentExprList => Indent.getSpaceIndent(indentCount)
      case _: ScDocComment => {
        if (child.getElementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS ||
                child.getElementType == ScalaDocTokenType.DOC_COMMENT_END) Indent.getSpaceIndent(1)
        else Indent.getNoneIndent
      }
      case _ => {
        node.getElementType match {
          case ScalaTokenTypes.kIF | ScalaTokenTypes.kELSE => {
            child.getPsi match {
              case _: ScExpression => Indent.getSpaceIndent(indentCount)
              case _ => Indent.getNoneIndent
            }
          }
          case _ => Indent.getNoneIndent
        }
      }
    }
  }
}