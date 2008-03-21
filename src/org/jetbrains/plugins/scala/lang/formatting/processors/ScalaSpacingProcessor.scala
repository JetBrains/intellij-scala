package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.formatting._;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.formatting.patterns._

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._;
import com.intellij.formatting.Spacing;

object ScalaSpacingProcessor extends ScalaTokenTypes {


  val NO_SPACING_WITH_NEWLINE = Spacing.createSpacing(0, 0, 0, true, 1);
  val NO_SPACING = Spacing.createSpacing(0, 0, 0, false, 0);
  val COMMON_SPACING = Spacing.createSpacing(1, 1, 0, true, 100);
  val IMPORT_BETWEEN_SPACING = Spacing.createSpacing(0, 0, 1, true, 100);
  val IMPORT_OTHER_SPACING = Spacing.createSpacing(0, 0, 2, true, 100);



  def getSpacing(left: ScalaBlock, right: ScalaBlock): Spacing = {

    val leftNode = left.getNode
    val rightNode = right.getNode

    leftNode.getElementType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        leftNode.getTreeParent.getElementType match {
          case ScalaElementTypes.LITERAL | ScalaElementTypes.PREFIX_EXPR | ScalaElementTypes.VARIANT_TYPE_PARAM => {
            return NO_SPACING
          }
          case _ =>
        }
      }
      case ScalaElementTypes.ANNOTATION => return IMPORT_BETWEEN_SPACING
      case ScalaTokenTypes.tLBRACE => {
        leftNode.getTreeParent().getElementType match {
          case ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT=> {
            return IMPORT_BETWEEN_SPACING
          }
          case  _:ScBlockExpr => {
            return IMPORT_BETWEEN_SPACING
          }
          case _ => {}
        }
        return NO_SPACING_WITH_NEWLINE
      }
      case ScalaTokenTypes.tSEMICOLON => {
        return IMPORT_BETWEEN_SPACING
      }
      case ScalaElementTypes.IMPORT_STMT => {
        rightNode.getElementType match {
          case ScalaElementTypes.IMPORT_STMT => {
            return IMPORT_BETWEEN_SPACING
          }
          case ScalaTokenTypes.tSEMICOLON => {
            return NO_SPACING
          }
          case _ => {
            return IMPORT_OTHER_SPACING
          }
        }
      }
      case ScalaTokenTypes.tDOT | ScalaTokenTypes.tLPARENTHESIS | ScalaTokenTypes.tLSQBRACKET => {
        return NO_SPACING_WITH_NEWLINE
      }
      case _ =>
    }

    rightNode.getPsi match {
      case _: ScParameters => {
        if (leftNode.getElementType == ScalaTokenTypes.tIDENTIFIER) return NO_SPACING
        leftNode.getPsi match {
          case _: ScReferenceId => return NO_SPACING
          case _ =>
        }
      }
      case _: ScCaseClause => {
        leftNode.getPsi match {
          case _: ScCaseClause => return IMPORT_BETWEEN_SPACING
          case _ =>
        }
      }
      case _ =>
    }

    rightNode.getElementType match {
      case ScalaTokenTypes.tRBRACE => {
        rightNode.getTreeParent().getElementType match {
          case ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT=> {
            return IMPORT_BETWEEN_SPACING
          }
          case  _:ScBlockExpr => {
            return IMPORT_BETWEEN_SPACING
          }
          case _ => {}
        }
        return NO_SPACING_WITH_NEWLINE
      }
      case ScalaTokenTypes.tDOT | ScalaTokenTypes.tCOMMA | ScalaTokenTypes.tCOLON |
           ScalaTokenTypes.tSEMICOLON | ScalaTokenTypes.tRPARENTHESIS |
           ScalaTokenTypes.tRSQBRACKET => {
        return NO_SPACING
      }
      case _ =>
    }

    return COMMON_SPACING
  }
}