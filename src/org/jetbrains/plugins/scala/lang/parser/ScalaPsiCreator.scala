package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
import org.jetbrains.plugins.scala.lang.psi.impl.top._, org.jetbrains.plugins.scala.lang.psi.impl.primitives._
import org.jetbrains.plugins.scala.lang.psi.impl.specialNodes.ScTrash
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

object ScalaPsiCreator {
  def createElement(node: ASTNode): PsiElement =

    node.getElementType() match {

      /*****************************************************/
      /************* COMPILATION UNIT **********************/

      case _ => inner(node)
    }


  //to prevent stack overflow in type checker let's introduce helper method
  private def inner(node: ASTNode): PsiElement = node.getElementType() match {



    /********************** TOKENS **********************/
    /********************* LITERALS *********************/
    case ScalaElementTypes.LITERAL => new ScLiteralImpl(node)

    /********************** TYPES ************************/

    case ScalaElementTypes.STABLE_ID => new ScStableIdImpl(node)
    case ScalaElementTypes.STABLE_ID_ID => new ScStableIdImpl(node)
    case ScalaElementTypes.PATH => new ScPathImpl(node)
    case ScalaElementTypes.SIMPLE_TYPE => new ScSimpleTypeImpl(node)
    case ScalaElementTypes.TYPE => new ScFunctionalTypeImpl(node)
    case ScalaElementTypes.COMPOUND_TYPE => new ScCompoundTypeImpl(node)
    case ScalaElementTypes.INFIX_TYPE => new ScInfixTypeImpl(node)
    case ScalaElementTypes.REFINE_STAT => new ScRefineStatImpl(node)
    case ScalaElementTypes.REFINEMENTS => new ScRefinementsImpl(node)
    case ScalaElementTypes.TYPES => new ScTypesImpl(node)
    case ScalaElementTypes.TYPE_ARGS => new ScTypeArgsImpl(node)

    /******************* EXPRESSIONS*********************/

    case ScalaElementTypes.PARENT_EXPR => new ScParenthesisedExpr(node)
    case ScalaElementTypes.METHOD_CALL => new ScMethodCallImpl(node)
    case ScalaElementTypes.REFERENCE_EXPRESSION => new ScReferenceExpressionImpl(node)
    case ScalaElementTypes.THIS_REFERENCE_EXPRESSION => new ScThisReferenceExpressionImpl(node)
    case ScalaElementTypes.PROPERTY_SELECTION => new ScPropertySelectionImpl(node)
    case ScalaElementTypes.GENERIC_CALL => new ScGenericCallImpl(node)

    case ScalaElementTypes.PREFIX_EXPR => new ScPrefixExprImpl(node)
    case ScalaElementTypes.SIMPLE_EXPR => new ScSimpleExprImpl(node)
    case ScalaElementTypes.UNIT => new ScUnitImpl(node)
    //    case ScalaElementTypes.PREFIX => new ScPrefixImpl(node)
    case ScalaElementTypes.INFIX_EXPR => new ScInfixExprImpl(node)
    case ScalaElementTypes.POSTFIX_EXPR => new ScPostfixExprImpl(node)
    case ScalaElementTypes.EXPR => new ScCommonExprImpl(node)
    case ScalaElementTypes.RESULT_EXPR => new ScResExprImpl(node)
    case ScalaElementTypes.BINDING => new ScBindingImpl(node)
    case ScalaElementTypes.ENUMERATOR => new ScEnumeratorImpl(node)
    case ScalaElementTypes.ENUMERATORS => new ScEnumeratorsImpl(node)
    case ScalaElementTypes.AN_FUN => new ScAnFunImpl(node)
    case ScalaElementTypes.EXPRS => new ScExprsImpl(node)
    case ScalaElementTypes.ARG_EXPRS => new ScArgumentExprsImpl(node)
    case ScalaElementTypes.BLOCK_EXPR => new ScBlockExprImpl(node)
    case ScalaElementTypes.BLOCK => new ScBlockImpl(node)
    case ScalaElementTypes.TUPLE => new ScTupleImpl(node)
    case ScalaElementTypes.BLOCK_STAT => new ScBlockStatImpl(node)
    case ScalaElementTypes.ERROR_STMT => new ScErrorStatImpl(node)

    case ScalaElementTypes.IF_STMT => new ScIfStmtImpl(node)
    case ScalaElementTypes.FOR_STMT => new ScForStmtImpl(node)
    case ScalaElementTypes.DO_STMT => new ScDoStmtImpl(node)
    case ScalaElementTypes.TRY_STMT => new ScTryStmtImpl(node)
    case ScalaElementTypes.TRY_BLOCK => new ScTryBlockImpl(node)
    case ScalaElementTypes.CATCH_BLOCK => new ScCatchBlockImpl(node)
    case ScalaElementTypes.FINALLY_BLOCK => new ScFinallyBlockImpl(node)
    case ScalaElementTypes.WHILE_STMT => new ScWhileStmtImpl(node)
    case ScalaElementTypes.METHOD_CLOSURE => new ScClosureImpl(node)
    case ScalaElementTypes.RETURN_STMT => new ScReturnStmtImpl(node)
    case ScalaElementTypes.THROW_STMT => new ScThrowStmtImpl(node)
    case ScalaElementTypes.ASSIGN_STMT => new ScAssignStmtImpl(node)
    case ScalaElementTypes.TYPED_EXPR_STMT => new ScTypedStmtImpl(node)
    case ScalaElementTypes.MATCH_STMT => new ScMatchStmtImpl(node)

    /******************* PATTERNS *********************/
    case ScalaElementTypes.SIMPLE_PATTERN => new ScTuplePatternImpl(node)
    case ScalaElementTypes.SIMPLE_PATTERN1 => new ScSimplePatternImpl(node)
    case ScalaElementTypes.PATTERN1 => new ScPattern1Impl(node)
    case ScalaElementTypes.PATTERN2 => new ScPattern2Impl(node)
    case ScalaElementTypes.PATTERN3 => new ScPattern3Impl(node)
    case ScalaElementTypes.PATTERN => new ScPatternImpl(node)
    case ScalaElementTypes.PATTERNS => new ScPatternsImpl(node)
    case ScalaElementTypes.WILD_PATTERN => new ScWildPatternImpl(node)
    case ScalaElementTypes.CASE_CLAUSE => new ScCaseClauseImpl(node)
    case ScalaElementTypes.CASE_CLAUSES => new ScCaseClausesImpl(node)

    case ScalaElementTypes.TYPE_PATTERN => new ScTypePatternImpl(node)
    case ScalaElementTypes.SIMPLE_TYPE_PATTERN => new ScSimpleTypePatternImpl(node)
    case ScalaElementTypes.SIMPLE_TYPE_PATTERN1 => new ScSimpleTypePattern1Impl(node)
    case ScalaElementTypes.TYPE_PATTERN_ARGS => new ScTypePatternArgsImpl(node)

    case ScalaElementTypes.TRASH => new ScTrash(node)

    case _ => new ScalaPsiElementImpl(node)

  }
}