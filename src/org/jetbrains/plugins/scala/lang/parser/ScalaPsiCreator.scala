package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.base._
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.ScalaDocPsiCreator

object ScalaPsiCreator extends ScalaPsiCreator {
  override protected val elementTypes = ScalaElementTypes

  override protected def classCase: ASTNode => ScClass = new ScClassImpl(_)

  override protected def traitCase: ASTNode => ScTrait = new ScTraitImpl(_)

  override protected def objectCase: ASTNode => ScObject = new ScObjectImpl(_)
}

trait ScalaPsiCreator extends PsiCreator {
  protected val elementTypes: ElementTypes

  protected def classCase: ASTNode => ScClass

  protected def objectCase: ASTNode => ScObject

  protected def traitCase: ASTNode => ScTrait

  def createElement(node: ASTNode): PsiElement =     
    node.getElementType match {
     case s: SelfPsiCreator => s.createElement(node)

     case _: ScalaDocElementType => ScalaDocPsiCreator.createElement(node)
    /*****************************************************/
    /********************** TOP **************************/
    /*****************************************************/

      case ScalaElementTypes.PACKAGING => new ScPackagingImpl(node)

      /***************************************************/
      /********************* IMPORT **********************/
      /***************************************************/

      case ScalaElementTypes.IMPORT_STMT => new ScImportStmtImpl(node)
      case ScalaElementTypes.IMPORT_EXPR => new ScImportExprImpl(node)
      case ScalaElementTypes.IMPORT_SELECTORS => new ScImportSelectorsImpl(node)
      case ScalaElementTypes.IMPORT_SELECTOR => new ScImportSelectorImpl(node)

      /***************************************************/
      /********************** DEF ************************/
      /***************************************************/

     case elementTypes.classDefinition => classCase(node)
     case elementTypes.objectDefinition => objectCase(node)
     case elementTypes.traitDefinition => traitCase(node)

      /***************** class ***************/
      case ScalaElementTypes.REQUIRES_BLOCK => new ScRequiresBlockImpl(node)
      case ScalaElementTypes.EXTENDS_BLOCK => new ScExtendsBlockImpl(node)

      /***************************************************/
      /******************** TEMPLATES ********************/
      /***************************************************/

      /******************* parents ****************/
      case ScalaElementTypes.CLASS_PARENTS => new ScClassParentsImpl(node)
      case ScalaElementTypes.TRAIT_PARENTS => new ScTraitParentsImpl(node)

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
      case ScalaElementTypes.MACRO_DEFINITION => new ScMacroDefinitionImpl(node)
      case ScalaElementTypes.TYPE_DEFINITION => new ScTypeAliasDefinitionImpl(node)
      case ScalaElementTypes.EARLY_DEFINITIONS => new ScEarlyDefinitionsImpl(node)

      /********** function definition: supplementary constructor *************/
      case ScalaElementTypes.SELF_INVOCATION => new ScSelfInvocationImpl(node)
      case ScalaElementTypes.CONSTR_EXPR => new ScConstrExprImpl(node)
      case ScalaElementTypes.PRIMARY_CONSTRUCTOR => new ScPrimaryConstructorImpl(node)

      /**************** function ******************/
      case ScalaElementTypes.CONSTRUCTOR => new ScConstructorImpl(node)

      /**************** variable ******************/
      case ScalaElementTypes.IDENTIFIER_LIST => new ScIdListImpl(node)
      case ScalaElementTypes.FIELD_ID => new ScFieldIdImpl(node)
      case ScalaElementTypes.REFERENCE => new ScStableCodeReferenceElementImpl(node)

      /**************** pattern ******************/
      case ScalaElementTypes.PATTERN_LIST => new ScPatternListImpl(node)

      /***************************************************/
      /********* PARAMETERS AND TYPE PARAMETERS **********/
      /***************************************************/

      /******************** parameters *******************/
      case ScalaElementTypes.PARAM_CLAUSE => new ScParameterClauseImpl(node)
      case ScalaElementTypes.PARAM_CLAUSES => new ScParametersImpl(node)
      /*********** class ************/
      case ScalaElementTypes.CLASS_PARAM => new ScClassParameterImpl(node)
      case ScalaElementTypes.PARAM => new ScParameterImpl(node)
      case ScalaElementTypes.PARAM_TYPE => new ScParameterTypeImpl(node)
      /***************** type parameters ****************/
      case ScalaElementTypes.TYPE_PARAM_CLAUSE => new ScTypeParamClauseImpl(node)
      /********** function **********/
      case ScalaElementTypes.TYPE_PARAM => new ScTypeParamImpl(node)
      /***************************************************/
      /************* MODIFIERS AND ATTRIBUTES ************/
      /***************************************************/

      /************** modifiers **************/
      case ScalaElementTypes.MODIFIERS => new ScModifierListImpl(node)
      case ScalaElementTypes.ACCESS_MODIFIER => new ScAccessModifierImpl(node)

      /************* annotation *************/
      case ScalaElementTypes.ANNOTATION => new ScAnnotationImpl(node)
      case ScalaElementTypes.ANNOTATION_EXPR => new ScAnnotationExprImpl(node)
      case ScalaElementTypes.ANNOTATIONS => new ScAnnotationsImpl(node)
      case ScalaElementTypes.NAME_VALUE_PAIR => new ScNameValuePairImpl(node)

      case _ => inner(node)
    }


  //to prevent stack overflow in type checker let's introduce helper method
  protected def inner(node: ASTNode): PsiElement = node.getElementType match {



  /********************** TOKENS **********************/
  /********************* LITERALS *********************/
    case ScalaElementTypes.LITERAL => new ScLiteralImpl(node)
    case ScalaElementTypes.INTERPOLATED_STRING_LITERAL => new ScInterpolatedStringLiteralImpl(node)
    case ScalaElementTypes.INTERPOLATED_PREFIX_PATTERN_REFERENCE => new ScInterpolatedPrefixReference(node)
    case ScalaElementTypes.INTERPOLATED_PREFIX_LITERAL_REFERENCE => new ScInterpolatedStringPartReference(node)

    /********************** TYPES ************************/

    case ScalaElementTypes.SIMPLE_TYPE => new ScSimpleTypeElementImpl(node)
    case ScalaElementTypes.TUPLE_TYPE => new ScTupleTypeElementImpl(node)
    case ScalaElementTypes.TYPE_IN_PARENTHESIS => new ScParenthesisedTypeElementImpl(node)
    case ScalaElementTypes.TYPE => new ScFunctionalTypeElementImpl(node)
    case ScalaElementTypes.COMPOUND_TYPE => new ScCompoundTypeElementImpl(node)
    case ScalaElementTypes.INFIX_TYPE => new ScInfixTypeElementImpl(node)
    case ScalaElementTypes.REFINEMENT => new ScRefinementImpl(node)
    case ScalaElementTypes.REFINEMENTS => new ScRefinementsImpl(node)
    case ScalaElementTypes.TYPES => new ScTypesImpl(node)
    case ScalaElementTypes.TYPE_ARGS => new ScTypeArgsImpl(node)
    case ScalaElementTypes.ASCRIPTION => new ScAscriptionImpl(node)
    case ScalaElementTypes.ANNOT_TYPE => new ScAnnotTypeElementImpl(node)
    case ScalaElementTypes.SEQUENCE_ARG => new ScSequenceArgImpl(node)
    case ScalaElementTypes.EXISTENTIAL_CLAUSE => new ScExistentialClauseImpl(node)
    case ScalaElementTypes.SELF_TYPE => new ScSelfTypeElementImpl(node)
    case ScalaElementTypes.EXISTENTIAL_TYPE => new ScExistentialTypeElementImpl(node)
    case ScalaElementTypes.WILDCARD_TYPE => new ScWildcardTypeElementImpl(node)
    case ScalaElementTypes.TYPE_PROJECTION => new ScTypeProjectionImpl(node)
    case ScalaElementTypes.TYPE_GENERIC_CALL => new ScParameterizedTypeElementImpl(node)
    case ScalaElementTypes.TYPE_VARIABLE => new ScTypeVariableTypeElementImpl(node)
    case _ => inner1(node)
  }

  //to prevent stack overflow in type checker let's introduce helper method
  private def inner1(node: ASTNode): PsiElement = node.getElementType match {

    /******************* EXPRESSIONS*********************/

    case ScalaElementTypes.PARENT_EXPR => new ScParenthesisedExprImpl(node)
    case ScalaElementTypes.METHOD_CALL => new ScMethodCallImpl(node)
    case ScalaElementTypes.REFERENCE_EXPRESSION => new ScReferenceExpressionImpl(node)
    case ScalaElementTypes.THIS_REFERENCE => new ScThisReferenceImpl(node)
    case ScalaElementTypes.SUPER_REFERENCE => new ScSuperReferenceImpl(node)
    case ScalaElementTypes.GENERIC_CALL => new ScGenericCallImpl(node)

    case ScalaElementTypes.PREFIX_EXPR => new ScPrefixExprImpl(node)
    case ScalaElementTypes.PLACEHOLDER_EXPR => new ScUnderscoreSectionImpl(node)
    case ScalaElementTypes.UNIT_EXPR => new ScUnitExprImpl(node)
    case ScalaElementTypes.INFIX_EXPR => new ScInfixExprImpl(node)
    case ScalaElementTypes.POSTFIX_EXPR => new ScPostfixExprImpl(node)
    case ScalaElementTypes.FUNCTION_EXPR => new ScFunctionExprImpl(node)
    case ScalaElementTypes.ENUMERATOR => new ScEnumeratorImpl(node)
    case ScalaElementTypes.ENUMERATORS => new ScEnumeratorsImpl(node)
    case ScalaElementTypes.GENERATOR => new ScGeneratorImpl(node)
    case ScalaElementTypes.GUARD => new ScGuardImpl(node)
    case ScalaElementTypes.EXPRS => new ScExprsImpl(node)
    case ScalaElementTypes.ARG_EXPRS => new ScArgumentExprListImpl(node)
    case ScalaElementTypes.BLOCK_EXPR => PsiUtilCore.NULL_PSI_ELEMENT
    case ScalaElementTypes.CONSTR_BLOCK => new ScConstrBlockImpl(node)
    case ScalaElementTypes.BLOCK => new ScBlockImpl(node)
    case ScalaElementTypes.TUPLE => new ScTupleImpl(node)
    case ScalaElementTypes.ERROR_STMT => new ScErrorStatImpl(node)

    case ScalaElementTypes.IF_STMT => new ScIfStmtImpl(node)
    case ScalaElementTypes.FOR_STMT => new ScForStatementImpl(node)
    case ScalaElementTypes.DO_STMT => new ScDoStmtImpl(node)
    case ScalaElementTypes.TRY_STMT => new ScTryStmtImpl(node)
    case ScalaElementTypes.TRY_BLOCK => new ScTryBlockImpl(node)
    case ScalaElementTypes.CATCH_BLOCK => new ScCatchBlockImpl(node)
    case ScalaElementTypes.FINALLY_BLOCK => new ScFinallyBlockImpl(node)
    case ScalaElementTypes.WHILE_STMT => new ScWhileStmtImpl(node)
    case ScalaElementTypes.RETURN_STMT => new ScReturnStmtImpl(node)
    case ScalaElementTypes.THROW_STMT => new ScThrowStmtImpl(node)
    case ScalaElementTypes.ASSIGN_STMT => new ScAssignStmtImpl(node)
    case ScalaElementTypes.TYPED_EXPR_STMT => new ScTypedStmtImpl(node)
    case ScalaElementTypes.MATCH_STMT => new ScMatchStmtImpl(node)
    case ScalaElementTypes.NEW_TEMPLATE => new ScNewTemplateDefinitionImpl(node)
    case _ => inner2(node)
  }

  //to prevent stack overflow in type checker let's introduce helper method
  private def inner2(node: ASTNode): PsiElement = node.getElementType match {
    /******************* PATTERNS *********************/
    case ScalaElementTypes.TUPLE_PATTERN => new ScTuplePatternImpl(node)
    case ScalaElementTypes.CONSTRUCTOR_PATTERN => new ScConstructorPatternImpl(node)
    case ScalaElementTypes.TYPED_PATTERN => new ScTypedPatternImpl(node)
    case ScalaElementTypes.NAMING_PATTERN => new ScNamingPatternImpl(node)
    case ScalaElementTypes.INFIX_PATTERN => new ScInfixPatternImpl(node)
    case ScalaElementTypes.PATTERN => new ScCompositePatternImpl(node)
    case ScalaElementTypes.PATTERN_ARGS => new ScPatternArgumentListImpl(node)
    case ScalaElementTypes.PATTERNS => new ScPatternsImpl(node)
    case ScalaElementTypes.WILDCARD_PATTERN => new ScWildcardPatternImpl(node)
    case ScalaElementTypes.SEQ_WILDCARD => new ScSeqWildcardImpl(node)
    case ScalaElementTypes.CASE_CLAUSE => new ScCaseClauseImpl(node)
    case ScalaElementTypes.CASE_CLAUSES => new ScCaseClausesImpl(node)
    case ScalaElementTypes.LITERAL_PATTERN => new ScLiteralPatternImpl(node)
    case ScalaElementTypes.INTERPOLATION_PATTERN => new ScInterpolationPatternImpl(node)
    case ScalaElementTypes.REFERENCE_PATTERN => new ScReferencePatternImpl(node)
    case ScalaElementTypes.STABLE_REFERENCE_PATTERN => new ScStableReferenceElementPatternImpl(node)
    case ScalaElementTypes.PATTERN_IN_PARENTHESIS => new ScParenthesisedPatternImpl(node)

    case ScalaElementTypes.TYPE_PATTERN => new ScTypePatternImpl(node)
    case ScalaElementTypes.TYPE_PATTERN_ARGS => new ScTypePatternArgsImpl(node)

    /********************* XML ************************/
    case ScalaElementTypes.XML_EXPR => new ScXmlExprImpl(node)
    case ScalaElementTypes.XML_START_TAG => new ScXmlStartTagImpl(node)
    case ScalaElementTypes.XML_END_TAG => new ScXmlEndTagImpl(node)
    case ScalaElementTypes.XML_EMPTY_TAG => new ScXmlEmptyTagImpl(node)
    case ScalaElementTypes.XML_PI => new ScXmlPIImpl(node)
    case ScalaElementTypes.XML_CD_SECT => new ScXmlCDSectImpl(node)
    case ScalaElementTypes.XML_ATTRIBUTE => new ScXmlAttributeImpl(node)
    case ScalaElementTypes.XML_PATTERN => new ScXmlPatternImpl(node)
    case ScalaElementTypes.XML_COMMENT => new ScXmlCommentImpl(node)
    case ScalaElementTypes.XML_ELEMENT => new ScXmlElementImpl(node)
    case _ => new ASTWrapperPsiElement(node)
  }

  trait SelfPsiCreator extends PsiCreator

}

trait PsiCreator {
  def createElement(node: ASTNode): PsiElement
}