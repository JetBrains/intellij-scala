package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.{ASTNode, Language}
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.{ICompositeElementType, IElementType, IErrorCounterReparseableElementType, IStubFileElementType}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.lexer.{ScalaElementType, ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaPsiCreator.SelfPsiCreator
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.base.{ScConstructorImpl, ScInterpolatedStringLiteralImpl, ScLiteralImpl, ScStableCodeReferenceElementImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterTypeImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScRequiresBlockImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures.{ScClassParameterElementType, ScParamClauseElementType, ScParamClausesElementType, ScParameterElementType}

/**
  * User: Dmitry.Krasilschikov
  * Date: 02.10.2006
  *
  */
object ScalaElementTypes extends ElementTypes {
  override val classDefinition = new ScClassDefinitionElementType
  override val objectDefinition = new ScObjectDefinitionElementType
  override val traitDefinition = new ScTraitDefinitionElementType

  val COMPOUND_TYPE = new ScalaElementType("compound type") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScCompoundTypeElementImpl(node)
  }
  val EXISTENTIAL_TYPE = new ScalaElementType("existential type") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScExistentialTypeElementImpl(node)
  }
  val EXISTENTIAL_CLAUSE = new ScalaElementType("existential clause") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScExistentialClauseImpl(node)
  }
}

trait ElementTypes {
  val DUMMY_ELEMENT = new ScalaElementType("Dummy Element")


  //Stub element types
  val FILE: IStubFileElementType[_ <: PsiFileStub[_ <: PsiFile]] = new ScStubFileElementType(ScalaFileType.SCALA_LANGUAGE)

  val classDefinition: ScTemplateDefinitionElementType[ScClass]
  val objectDefinition: ScTemplateDefinitionElementType[ScObject]
  val traitDefinition: ScTemplateDefinitionElementType[ScTrait]

  val PACKAGING = new ScPackagingElementType
  val EXTENDS_BLOCK = new ScExtendsBlockElementType

  val CLASS_PARENTS = new ScClassParentsElementType
  val TRAIT_PARENTS = new ScTraitParentsElementType
  val CONSTRUCTOR = new ScalaElementType("constructor", true) with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScConstructorImpl(node)
  }

  val TEMPLATE = new ScalaElementType("template", true)
  val TEMPLATE_BODY = new ScTemplateBodyElementType


  val REQUIRES_BLOCK = new ScalaElementType("requires block") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScRequiresBlockImpl(node)
  }
  val NEW_TEMPLATE = new ScNewTemplateDefinitionStubElementType


  /** ***********************************************************************************/
  /** *********************** PARAMETERS OF CLASS AND FUNCTIONS *************************/
  /** ***********************************************************************************/

  val PARAM_TYPE = new ScalaElementType("parameter type") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScParameterTypeImpl(node)
  }
  val PARAM = new ScParameterElementType
  val PARAM_CLAUSE = new ScParamClauseElementType
  val PARAM_CLAUSES = new ScParamClausesElementType

  /** ********** class ***************/
  val CLASS_PARAM = new ScClassParameterElementType

  /** ***********************************************************************************/
  /** *********************** TYPE PARAMETERS OF CLASS AND FUNCTIONS *************************/
  /** ***********************************************************************************/
  val TYPE_PARAM_CLAUSE = new ScTypeParamClauseElementType

  /** ********** class ***************/
  val VARIANT_TYPE_PARAM = new ScalaElementType("variant parameter of type")

  /** ********** function *************/
  val TYPE_PARAM = new ScTypeParamElementType
  val TYPE_PARAMS = new ScalaElementType("parameters of type")

  /** ***********************************************************************************/
  /** ************************************ TYPES ****************************************/
  /** ***********************************************************************************/
  val SIMPLE_TYPE = new ScalaElementType("simple type")
  val INFIX_TYPE = new ScalaElementType("infix type")
  val TYPE = new ScalaElementType("common type")
  val TYPES = new ScalaElementType("common type") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTypesImpl(node)
  }
  val TYPE_ARGS = new ScalaElementType("type arguments")
  val ANNOT_TYPE = new ScalaElementType("annotation type")
  val SELF_TYPE = new ScSelfTypeElementElementType
  val WILDCARD_TYPE = new ScalaElementType("wildcard type")
  val ASCRIPTION = new ScalaElementType("ascription") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScAscriptionImpl(node)
  }
  val TUPLE_TYPE = new ScalaElementType("tuple type")
  val TYPE_IN_PARENTHESIS = new ScalaElementType("type in parenthesis") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScParenthesisedTypeElementImpl(node)
  }
  val PRIMARY_CONSTRUCTOR = new ScPrimaryConstructorElementType
  val TYPE_PROJECTION = new ScalaElementType("type projection")
  val TYPE_GENERIC_CALL = new ScalaElementType("type generic call")
  val SEQUENCE_ARG = new ScalaElementType("sequence argument type") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScSequenceArgImpl(node)
  }
  val TYPE_VARIABLE = new ScalaElementType("type variable")

  /** ***********************************************************************************/
  /** ********************************* IDENTIFIER **************************************/
  /** ***********************************************************************************/

  val UNIT_EXPR = new ScalaElementType("unit expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScUnitExprImpl(node)
  }
  val IDENTIFIER_LIST = new ScIdListElementType
  val FIELD_ID = new ScFieldIdElementType
  val REFERENCE = new ScalaElementType("reference") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScStableCodeReferenceElementImpl(node)
  }

  /** ***********************************************************************************/
  /** ******************************* IMPORT GROUP **************************************/
  /** ***********************************************************************************/

  val IMPORT_SELECTOR = new ScImportSelectorElementType
  val IMPORT_SELECTORS = new ScImportSelectorsElementType
  val IMPORT_EXPR = new ScImportExprElementType
  val IMPORT_STMT = new ScImportStmtElementType
  val IMPORT = new ScalaElementType("import")
  val STABLE_ID_LIST = new ScalaElementType("stable id list")

  /** ***********************************************************************************/
  /** ******************************* METHODS, VARIABLES and ETC ************************/
  /** ***********************************************************************************/
  val STATEMENT_TEMPLATE = new ScalaElementType("template statement")

  /** ***********************************************************************************/
  /** ********************************** DECLARATION ************************************/
  /** ***********************************************************************************/
  val VALUE_DECLARATION: ScValueElementType[_ <: ScValue] = new ScValueDeclarationElementType
  val VARIABLE_DECLARATION: ScVariableElementType[_ <: ScVariable] = new ScVariableDeclarationElementType
  val FUNCTION_DECLARATION: ScFunctionElementType[_ <: ScFunction] = new ScFunctionDeclarationElementType
  val TYPE_DECLARATION = new ScTypeAliasDeclarationElementType

  /** ***********************************************************************************/
  /** ********************************** DEFINITION *************************************/
  /** ***********************************************************************************/
  val PATTERN_DEFINITION: ScValueElementType[_ <: ScValue] = new ScValueDefinitionElementType
  val PATTERN_LIST = new ScPatternListElementType
  val VARIABLE_DEFINITION: ScVariableElementType[_ <: ScVariable] = new ScVariableDefinitionElementType
  val TYPE_DEFINITION = new ScTypeAliasDefinitionElementType
  val EARLY_DEFINITIONS = new ScEarlyDefinitionsElementType

  /** ************** functions *************************/
  val FUNCTION_DEFINITION = new ScFunctionDefinitionElementType
  val MACRO_DEFINITION = new ScMacroDefinitionElementType
  val FUN_SIG = new ScalaElementType("function signature")
  val CONSTR_EXPR = new ScalaElementType("constructor expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScConstrExprImpl(node)
  }
  val SELF_INVOCATION = new ScalaElementType("self invocation") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScSelfInvocationImpl(node)
  }

  /** *************** types ******************/
  val LOWER_BOUND_TYPE = new ScalaElementType("lower bound type")
  val UPPER_BOUND_TYPE = new ScalaElementType("upper bound type")

  /** ***********************************************************************************/
  /** ***************************** MODIFIERS AND ATTRIBUTES ****************************/
  /** ***********************************************************************************/

  /** ***************** modifiers **********************/
  val MODIFIERS = new ScModifiersElementType("moifiers")
  val ACCESS_MODIFIER = new ScAccessModifierElementType

  /** ***************** annotation *********************/

  val NAME_VALUE_PAIR = new ScalaElementType("name value pair") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScNameValuePairImpl(node)
  }
  val ANNOTATION_EXPR = new ScalaElementType("annotation expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScAnnotationExprImpl(node)
  }
  val ANNOTATION = new ScAnnotationElementType
  val ANNOTATIONS = new ScAnnotationsElementType

  /** ***********************************************************************************/
  /** ************************************ LITERALS *************************************/
  /** ***********************************************************************************/
  val LITERAL = new ScalaElementType("Literal") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScLiteralImpl(node)
  }
  //  String literals
  val STRING_LITERAL = new ScalaElementType("String Literal")
  val INTERPOLATED_STRING_LITERAL = new ScalaElementType("Interpolated String Literal") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScInterpolatedStringLiteralImpl(node)
  }
  //Not only String, but quasiquote too
  val INTERPOLATED_PREFIX_PATTERN_REFERENCE = new ScalaElementType("Interpolated Prefix Pattern Reference") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScInterpolatedPrefixReference(node)
  }
  val INTERPOLATED_PREFIX_LITERAL_REFERENCE = new ScalaElementType("Interpolated Prefix Literal Reference") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScInterpolatedStringPartReference(node)
  }
  // Boolean literals
  val BOOLEAN_LITERAL = new ScalaElementType("Boolean Literal")

  /** ***********************************************************************************/
  /** ************************************ EXPRESSIONS **********************************/
  /** ***********************************************************************************/
  /**/
  val PREFIX_EXPR = new ScalaElementType("prefix expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScPrefixExprImpl(node)
  }
  val PREFIX = new ScalaElementType("prefix")
  val POSTFIX_EXPR = new ScalaElementType("postfix expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScPostfixExprImpl(node)
  }
  val INFIX_EXPR = new ScalaElementType("infix expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScInfixExprImpl(node)
  }
  val PLACEHOLDER_EXPR = new ScalaElementType("simple expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScUnderscoreSectionImpl(node)
  }

  val PARENT_EXPR = new ScalaElementType("Expression in parentheses") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScParenthesisedExprImpl(node)
  }
  val METHOD_CALL = new ScalaElementType("Method call") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScMethodCallImpl(node)
  }
  val REFERENCE_EXPRESSION = new ScalaElementType("Reference expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScReferenceExpressionImpl(node)
  }
  val THIS_REFERENCE = new ScalaElementType("This reference") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScThisReferenceImpl(node)
  }
  val SUPER_REFERENCE = new ScalaElementType("Super reference") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScSuperReferenceImpl(node)
  }
  val GENERIC_CALL = new ScalaElementType("Generified call") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScGenericCallImpl(node)
  }

  val EXPR1 = new ScalaElementType("composite expression ")
  val FUNCTION_EXPR = new ScalaElementType("expression") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScFunctionExprImpl(node)
  }
  val AN_FUN = new ScalaElementType("anonymous function")
  val GENERATOR = new ScalaElementType("generator") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScGeneratorImpl(node)
  }
  val ENUMERATOR = new ScalaElementType("enumerator") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScEnumeratorImpl(node)
  }
  val ENUMERATORS = new ScalaElementType("enumerator") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScEnumeratorsImpl(node)
  }
  val GUARD = new ScalaElementType("guard") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScGuardImpl(node)
  }
  val EXPRS = new ScalaElementType("list of expressions") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScExprsImpl(node)
  }
  val ARG_EXPRS = new ScalaElementType("arguments of function") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScArgumentExprListImpl(node)
  }
  val BLOCK_EXPR = new ScCodeBlockElementType
  val CONSTR_BLOCK = new ScalaElementType("constructor block") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScConstrBlockImpl(node)
  }
  val ERROR_STMT = new ScalaElementType("error statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScErrorStatImpl(node)
  }
  val BLOCK = new ScalaElementType("block") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScBlockImpl(node)
  }
  val TUPLE = new ScalaElementType("Tuple") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTupleImpl(node)
  }

  /** ****************************** COMPOSITE EXPRESSIONS *****************************/
  val IF_STMT = new ScalaElementType("if statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScIfStmtImpl(node)
  }
  val FOR_STMT = new ScalaElementType("for statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScForStatementImpl(node)
  }
  val DO_STMT = new ScalaElementType("do-while statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScDoStmtImpl(node)
  }
  val TRY_STMT = new ScalaElementType("try statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTryStmtImpl(node)
  }
  val TRY_BLOCK = new ScalaElementType("try block") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTryBlockImpl(node)
  }
  val CATCH_BLOCK = new ScalaElementType("catch block") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScCatchBlockImpl(node)
  }
  val FINALLY_BLOCK = new ScalaElementType("finally block") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScFinallyBlockImpl(node)
  }
  val WHILE_STMT = new ScalaElementType("while statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScWhileStmtImpl(node)
  }
  val RETURN_STMT = new ScalaElementType("return statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScReturnStmtImpl(node)
  }
  val THROW_STMT = new ScalaElementType("throw statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScThrowStmtImpl(node)
  }
  val ASSIGN_STMT = new ScalaElementType("assign statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScAssignStmtImpl(node)
  }
  val MATCH_STMT = new ScalaElementType("match statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScMatchStmtImpl(node)
  }
  val TYPED_EXPR_STMT = new ScalaElementType("typed statement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTypedStmtImpl(node)
  }


  /** ***********************************************************************************/
  /** ************************************ PATTERNS *************************************/
  /** ***********************************************************************************/

  val TUPLE_PATTERN = new ScalaElementType("Tuple Pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTuplePatternImpl(node)
  }
  val SEQ_WILDCARD = new ScalaElementType("Sequence Wildcard") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScSeqWildcardImpl(node)
  }
  val CONSTRUCTOR_PATTERN = new ScalaElementType("Constructor Pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScConstructorPatternImpl(node)
  }
  val PATTERN_ARGS = new ScalaElementType("Pattern arguments") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScPatternArgumentListImpl(node)
  }
  val INFIX_PATTERN = new ScalaElementType("Infix pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScInfixPatternImpl(node)
  }
  val NAMING_PATTERN = new ScalaElementType("Binding Pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScNamingPatternImpl(node)
  }
  val TYPED_PATTERN = new ScalaElementType("Typed Pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTypedPatternImpl(node)
  }
  val PATTERN = new ScalaElementType("Composite Pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScCompositePatternImpl(node)
  }
  val PATTERNS = new ScalaElementType("patterns") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScPatternsImpl(node)
  }
  val WILDCARD_PATTERN = new ScalaElementType("any sequence") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScWildcardPatternImpl(node)
  }
  val CASE_CLAUSE = new ScalaElementType("case clause") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScCaseClauseImpl(node)
  }
  val CASE_CLAUSES = new ScalaElementType("case clauses") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScCaseClausesImpl(node)
  }
  val LITERAL_PATTERN = new ScalaElementType("literal pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScLiteralPatternImpl(node)
  }
  val INTERPOLATION_PATTERN = new ScalaElementType("interpolation pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScInterpolationPatternImpl(node)
  }
  val REFERENCE_PATTERN = new ScReferencePatternElementType
  val STABLE_REFERENCE_PATTERN = new ScalaElementType("stable reference pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScStableReferenceElementPatternImpl(node)
  }
  val PATTERN_IN_PARENTHESIS = new ScalaElementType("pattern in parenthesis") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScParenthesisedPatternImpl(node)
  }

  /** ************************************ TYPE PATTERNS ********************************/

  val TYPE_PATTERN_ARGS = new ScalaElementType("Type pattern arguments") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTypePatternArgsImpl(node)
  }
  val TYPE_PATTERN = new ScalaElementType("Type pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScTypePatternImpl(node)
  }

  val REFINEMENT = new ScalaElementType("refinement") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScRefinementImpl(node)
  }

  /** ************************************* XML *************************************/

  val XML_EXPR = new ScalaElementType("Xml expr") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlExprImpl(node)
  }
  val XML_START_TAG = new ScalaElementType("Xml start tag") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlStartTagImpl(node)
  }
  val XML_END_TAG = new ScalaElementType("Xml end tag") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlEndTagImpl(node)
  }
  val XML_EMPTY_TAG = new ScalaElementType("Xml empty tag") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlEmptyTagImpl(node)
  }
  val XML_PI = new ScalaElementType("Xml proccessing instruction") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlPIImpl(node)
  }
  val XML_CD_SECT = new ScalaElementType("Xml cdata section") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlCDSectImpl(node)
  }
  val XML_ATTRIBUTE = new ScalaElementType("Xml attribute") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlAttributeImpl(node)
  }
  val XML_PATTERN = new ScalaElementType("Xml pattern") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlPatternImpl(node)
  }
  val XML_COMMENT = new ScalaElementType("Xml comment") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlCommentImpl(node)
  }
  val XML_ELEMENT = new ScalaElementType("Xml element") with SelfPsiCreator {
    override def createElement(node: ASTNode): PsiElement = new ScXmlElementImpl(node)
  }

  class ScCodeBlockElementType()
    extends IErrorCounterReparseableElementType("block of expressions", ScalaFileType.SCALA_LANGUAGE)
      with ICompositeElementType {

    override def createNode(text: CharSequence): ASTNode = new ScBlockExprImpl(text)

    @NotNull override def createCompositeNode: ASTNode = new ScBlockExprImpl(null)

    override def getErrorsCount(seq: CharSequence, fileLanguage: Language, project: Project): Int = {
      import com.intellij.psi.tree.IErrorCounterReparseableElementType._
      val lexer: Lexer = new ScalaLexer
      lexer.start(seq)
      if (lexer.getTokenType != ScalaTokenTypes.tLBRACE) return FATAL_ERROR
      lexer.advance()
      var balance: Int = 1
      var flag = false
      while (!flag) {
        val tp: IElementType = lexer.getTokenType
        if (tp == null) flag = true
        else if (balance == 0) return FATAL_ERROR
        else if (tp == ScalaTokenTypes.tLBRACE) {
          balance += 1
        } else if (tp == ScalaTokenTypes.tRBRACE) {
          balance -= 1
        }
        lexer.advance()
      }
      balance
    }
  }

}