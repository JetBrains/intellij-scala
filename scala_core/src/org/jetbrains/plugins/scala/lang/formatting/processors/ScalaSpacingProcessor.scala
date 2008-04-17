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

    val (leftString,rightString) = (leftNode.toString,rightNode.toString)//for debug

    (leftNode.getElementType, rightNode.getElementType,
      leftNode.getTreeParent.getElementType, rightNode.getTreeParent.getElementType) match {
      //class params
      case (ScalaTokenTypes.tIDENTIFIER | ScalaElementTypes.TYPE_PARAM_CLAUSE, ScalaElementTypes.PRIMARY_CONSTRUCTOR,_,_)
        if !rightNode.getPsi.asInstanceOf[ScPrimaryConstructor].hasAnnotation &&
           !rightNode.getPsi.asInstanceOf[ScPrimaryConstructor].hasModifier => return NO_SPACING
      //Type*
      case (_,ScalaTokenTypes.tIDENTIFIER,_,ScalaElementTypes.PARAM_TYPE) if (rightNode.getText == "*") => return NO_SPACING
      //Parameters
      case (ScalaTokenTypes.tIDENTIFIER, ScalaElementTypes.PARAM_CLAUSES,_,_) => return NO_SPACING
      case (_, ScalaElementTypes.TYPE_ARGS,_,(ScalaElementTypes.TYPE_GENERIC_CALL | ScalaElementTypes.GENERIC_CALL)) => return NO_SPACING
      case (_, ScalaElementTypes.PATTERN_ARGS,_,ScalaElementTypes.CONSTRUCTOR_PATTERN) => return NO_SPACING
      //Annotation
      case (ScalaTokenTypes.tAT,_,_,_) => return NO_SPACING
      case (ScalaTokenTypes.tIDENTIFIER,ScalaTokenTypes.tAT,ScalaElementTypes.BINDING_PATTERN,_) => return NO_SPACING
      case (_,ScalaTokenTypes.tAT,_,_) => return NO_SPACING_WITH_NEWLINE
      case (ScalaElementTypes.ANNOTATION,_,_,_) => return COMMON_SPACING
      //Prefix Identifier
      case (ScalaTokenTypes.tIDENTIFIER,_,
        (ScalaElementTypes.LITERAL | ScalaElementTypes.PREFIX_EXPR
        | ScalaElementTypes.VARIANT_TYPE_PARAM),_) => return NO_SPACING
      //Braces
      case (ScalaTokenTypes.tLBRACE,ScalaTokenTypes.tRBRACE,_,_) => NO_SPACING
      case (ScalaTokenTypes.tLBRACE,_,
        (ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT |
          ScalaElementTypes.EXISTENTIAL_CLAUSE | ScalaElementTypes.BLOCK_EXPR),_) => return IMPORT_BETWEEN_SPACING
      case (ScalaTokenTypes.tLBRACE,_,_,_) => return NO_SPACING_WITH_NEWLINE
      case (_,ScalaTokenTypes.tRBRACE,(ScalaElementTypes.TEMPLATE_BODY | ScalaElementTypes.MATCH_STMT | ScalaElementTypes.REFINEMENT |
          ScalaElementTypes.EXISTENTIAL_CLAUSE | ScalaElementTypes.BLOCK_EXPR),_) => return IMPORT_BETWEEN_SPACING
      case (_,ScalaTokenTypes.tRBRACE,_,_) => return NO_SPACING_WITH_NEWLINE
      //Semicolon
      case (ScalaTokenTypes.tSEMICOLON,_,_,_) => return IMPORT_BETWEEN_SPACING
      case (_,ScalaTokenTypes.tSEMICOLON,_,_) => return NO_SPACING
      //Imports
      case (ScalaElementTypes.IMPORT_STMT,ScalaElementTypes.IMPORT_STMT,_,_) => return IMPORT_BETWEEN_SPACING
      case (ScalaElementTypes.IMPORT_STMT,_,_,_) => return IMPORT_OTHER_SPACING
      //Dot
      case (ScalaTokenTypes.tDOT,_,_,_) => return NO_SPACING_WITH_NEWLINE
      case (_,ScalaTokenTypes.tDOT,_,_) => return NO_SPACING
      //Comma
      case (ScalaTokenTypes.tCOMMA,_,_,_) => return COMMON_SPACING
      case (_,ScalaTokenTypes.tCOMMA,_,_) => return NO_SPACING
      //Parenthesises and Brackets
      case ((ScalaTokenTypes.tLPARENTHESIS | ScalaTokenTypes.tLSQBRACKET),_,_,_) => return NO_SPACING_WITH_NEWLINE
      case (_,ScalaTokenTypes.tLSQBRACKET,_,_) => return NO_SPACING
      case (_, ScalaTokenTypes.tLPARENTHESIS,ScalaElementTypes.CONSTRUCTOR_PATTERN,_) => return NO_SPACING
      case (_,ScalaTokenTypes.tLPARENTHESIS,_,_) if (rightNode.getPsi match {
        case _: ScArguments | _: ScParameters => true
        case _ => false
      }) => return NO_SPACING
      case ((ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET),_,_,_) => return COMMON_SPACING
      case (_,(ScalaTokenTypes.tRPARENTHESIS | ScalaTokenTypes.tRSQBRACKET),_,_) => return NO_SPACING_WITH_NEWLINE
      //Case clauses
      case (ScalaElementTypes.CASE_CLAUSE,_,_,_) => return IMPORT_BETWEEN_SPACING
      case (_,ScalaElementTypes.CASE_CLAUSE,_,_) => return IMPORT_BETWEEN_SPACING
      //Colon
      case (_,ScalaTokenTypes.tCOLON,_,_) => return NO_SPACING
      //#
      case (ScalaTokenTypes.tINNER_CLASS,_,_,_) => return NO_SPACING
      case (_,ScalaTokenTypes.tINNER_CLASS,_,_) => return NO_SPACING
      //Other cases
      case _ => return COMMON_SPACING
    }
  }
}