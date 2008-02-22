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

import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.base._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.base.types._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

object ScalaPsiCreator {
  def createElement(node: ASTNode): PsiElement =

    node.getElementType() match {

      /*****************************************************/
      /************* COMPILATION UNIT **********************/

      case ScalaElementTypes.COMPILATION_UNIT => new ScCompilationUnit(node) //not used

      /*****************************************************/
      /********************** TOP **************************/
      /*****************************************************/

      case ScalaElementTypes.PACKAGING => new ScPackagingImpl(node)
      case ScalaElementTypes.QUAL_ID => new ScQualIdImpl(node)

      /***************************************************/
      /********************* IMPORT **********************/
      /***************************************************/

      case ScalaElementTypes.IMPORT_STMT => new ScImportStmtImpl(node)
      case ScalaElementTypes.IMPORT_EXPR => new ScImportExprImpl(node)
      case ScalaElementTypes.IMPORT_EXPRS => new ScImportExprs(node) //not used
      case ScalaElementTypes.IMPORT_END => new ScImportEndId(node) //not used

      case ScalaElementTypes.IMPORT_SELECTORS => new ScImportSelectorsImpl(node)
      case ScalaElementTypes.IMPORT_SELECTOR => new ScImportSelectorImpl(node)
      case ScalaElementTypes.IMPORT_SELECTOR_BEGIN => new ScSelectorBeginId(node) //not used

      /***************************************************/
      /********************** DEF ************************/
      /***************************************************/

      case ScalaElementTypes.PACKAGE_STMT => new ScPackageStatementImpl(node)
      case ScalaElementTypes.CLASS_DEF => new ScClassImpl(node)
      case ScalaElementTypes.OBJECT_DEF => new ScObjectImpl(node)
      case ScalaElementTypes.TRAIT_DEF => new ScTraitImpl(node)

      /***************** class ***************/
      case ScalaElementTypes.REQUIRES_BLOCK => new ScRequiresBlockImpl(node)
      case ScalaElementTypes.EXTENDS_BLOCK => new ScExtendsBlockImpl(node)

      /***************************************************/
      /******************** TEMPLATES ********************/
      /***************************************************/

      case ScalaElementTypes.TOP_DEF_TEMPLATE => new ScTopDefTemplate(node) //not used

      /******************* parents ****************/
      case ScalaElementTypes.TEMPLATE_PARENTS => new ScTemplateParentsImpl(node)
      case ScalaElementTypes.MIXIN_PARENTS => new ScMixinParentsImpl(node)

      /******************* body *******************/
      case ScalaElementTypes.TEMPLATE_BODY => new ScTemplateBodyImpl(node)


      /***************************************************/
      /*************** TEMPLATE STATEMENTS ***************/
      /***************************************************/

      /*************** DECLARATION ***************/
      case ScalaElementTypes.VALUE_DECLARATION => new ScValueDeclarationImpl(node)
      case ScalaElementTypes.VARIABLE_DECLARATION => new ScVariableDeclarationImpl(node)
      case ScalaElementTypes.FUNCTION_DECLARATION => new ScFunctionDeclarationImpl(node)
      case ScalaElementTypes.TYPE_DECLARATION => new ScTypeAliasDeclarationImpl(node)

      /*************** DEFINITION ***************/
      case ScalaElementTypes.PATTERN_DEFINITION => new ScPatternDefinitionImpl(node)
      case ScalaElementTypes.VARIABLE_DEFINITION => new ScVariableDefinitionImpl(node)
      case ScalaElementTypes.FUNCTION_DEFINITION => new ScFunctionDefinitionImpl(node)
      case ScalaElementTypes.TYPE_DEFINITION => new ScTypeAliasDefinitionImpl(node)
      case ScalaElementTypes.EARLY_DEFINITION => new ScEarlyDefinitionImpl(node)

      /********** function definition: supplementary constructor *************/
      case ScalaElementTypes.SELF_INVOCATION => new ScSelfInvocationImpl(node)
      case ScalaElementTypes.CONSTR_EXPR => new ScConstrExprImpl(node)
      case ScalaElementTypes.SUPPLEMENTARY_CONSTRUCTOR => new ScSupplementaryConstructor(node) //not used

      /**************** function ******************/
      case ScalaElementTypes.CONSTRUCTOR => new ScConstructorImpl(node)

      /**************** variable ******************/
      case ScalaElementTypes.IDENTIFIER_LIST => new ScIdListImpl(node)
      case ScalaElementTypes.REFERENCE => new ScReferenceIdImpl(node)

      /**************** pattern ******************/
      case ScalaElementTypes.PATTERN2_LIST => new ScPatternListImpl(node)

      /**************** bounds *******************/
      case ScalaElementTypes.LOWER_BOUND_TYPE => new ScLowerBoundImpl(node) //not used
      case ScalaElementTypes.UPPER_BOUND_TYPE => new ScUpperBoundImpl(node) //not used

      /***************************************************/
      /********* PARAMETERS AND TYPE PARAMETERS **********/
      /***************************************************/

      /******************** parameters *******************/
      case ScalaElementTypes.PARAM_CLAUSE => new ScParamClauseImpl(node)
      case ScalaElementTypes.PARAM_CLAUSES => new ScParamClausesImpl(node)
      /*********** class ************/
      case ScalaElementTypes.CLASS_PARAM => new ScClassParamImpl(node)
      case ScalaElementTypes.CLASS_PARAM_CLAUSE => new ScClassParamClauseImpl(node)
      case ScalaElementTypes.CLASS_PARAM_CLAUSES => new ScClassParamClausesImpl(node)
      /********** function **********/
      case ScalaElementTypes.PARAM => new ScParamImpl(node)
      case ScalaElementTypes.PARAM_TYPE => new ScParamTypeImpl(node)
      /***************** type parameters ****************/
      case ScalaElementTypes.TYPE_PARAM_CLAUSE => new ScTypeParamClauseImpl(node)
      /*********** class ************/
      case ScalaElementTypes.VARIANT_TYPE_PARAM => new ScVariantTypeParamImpl(node)
      /********** function **********/
      case ScalaElementTypes.TYPE_PARAM => new ScTypeParamImpl(node)

      case ScalaElementTypes.PARAMS => new ScParamsImpl(node)

      /***************************************************/
      /************* MODIFIERS AND ATTRIBUTES ************/
      /***************************************************/

      /************** modifiers **************/
      case ScalaElementTypes.MODIFIERS => new ScModifiersImpl(node)
      /************** attributes **************/
      case ScalaElementTypes.ATTRIBUTE => new ScAttribute(node) //not used
      case ScalaElementTypes.ATTRIBUTE_CLAUSE => new ScAttributeClause(node) //not used
      case ScalaElementTypes.ATTRIBUTE_CLAUSES => new ScAttributeClauses(node) //not used

      case _ => inner(node)
    }


  //to prevent stack overflow in type checker let's introduce helper method
  private def inner(node: ASTNode): PsiElement = node.getElementType() match {



    /********************** TOKENS **********************/
    /********************* LITERALS *********************/
    case ScalaElementTypes.LITERAL => new ScLiteralImpl(node)

    /********************** TYPES ************************/

    case ScalaElementTypes.STABLE_ID => new ScStableIdImpl(node)
    case ScalaElementTypes.STABLE_ID_ID => new ScStableIdImpl(node) //not used
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
    case ScalaElementTypes.GUARD => new ScGuardImpl(node)
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
    case ScalaElementTypes.NEW_TEMPLATE => new ScNewTemplateDefinition(node)

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
    case ScalaElementTypes.LITERAL_PATTERN => new ScLiteralPatternImpl(node)
    case ScalaElementTypes.REFERENCE_PATTERN => new ScReferencePatternImpl(node)

    case ScalaElementTypes.TYPE_PATTERN => new ScTypePatternImpl(node)
    case ScalaElementTypes.SIMPLE_TYPE_PATTERN => new ScSimpleTypePatternImpl(node)
    case ScalaElementTypes.SIMPLE_TYPE_PATTERN1 => new ScSimpleTypePattern1Impl(node)
    case ScalaElementTypes.TYPE_PATTERN_ARGS => new ScTypePatternArgsImpl(node)

    case ScalaElementTypes.TRASH => new ScTrash(node)

    case _ => new ScalaPsiElementImpl(node)

  }
}