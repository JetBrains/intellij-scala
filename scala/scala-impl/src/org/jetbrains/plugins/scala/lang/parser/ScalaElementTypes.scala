package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.{ASTNode, Language}
import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree._
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.base._
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterTypeImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures._

import scala.annotation.tailrec

/**
  * User: Dmitry.Krasilschikov
  * Date: 02.10.2006
  *
  */
object ScalaElementTypes {

  val IDENTIFIER_LIST = new ScIdListElementType
  val FIELD_ID = new ScFieldIdElementType
  val IMPORT_SELECTOR = new ScImportSelectorElementType
  val IMPORT_SELECTORS = new ScImportSelectorsElementType
  val IMPORT_EXPR = new ScImportExprElementType
  val IMPORT_STMT = new ScImportStmtElementType
  val VALUE_DECLARATION: ScPropertyElementType[ScValueDeclaration] = ValueDeclaration
  val PATTERN_DEFINITION: ScPropertyElementType[ScPatternDefinition] = ValueDefinition
  val VARIABLE_DECLARATION: ScPropertyElementType[ScVariableDeclaration] = VariableDeclaration
  val VARIABLE_DEFINITION: ScPropertyElementType[ScVariableDefinition] = VariableDefinition
  val FUNCTION_DECLARATION = new ScFunctionDeclarationElementType
  val TYPE_DECLARATION = new ScTypeAliasDeclarationElementType
  val PATTERN_LIST = new ScPatternListElementType
  val TYPE_DEFINITION = new ScTypeAliasDefinitionElementType
  val EARLY_DEFINITIONS = new ScEarlyDefinitionsElementType
  val FUNCTION_DEFINITION = new ScFunctionDefinitionElementType
  val MACRO_DEFINITION = new ScMacroDefinitionElementType
  val MODIFIERS = new ScModifiersElementType("moifiers")
  val ACCESS_MODIFIER = new ScAccessModifierElementType
  val ANNOTATION = new ScAnnotationElementType
  val ANNOTATIONS = new ScAnnotationsElementType
  val REFERENCE_PATTERN = new ScReferencePatternElementType
  val PACKAGING = new ScPackagingElementType
  val EXTENDS_BLOCK = new ScExtendsBlockElementType
  val TEMPLATE_PARENTS = new ScTemplateParentsElementType
  val TEMPLATE_BODY = new ScTemplateBodyElementType
  val PARAM = new ScParameterElementType
  val PARAM_CLAUSE = new ScParamClauseElementType
  val PARAM_CLAUSES = new ScParamClausesElementType
  val CLASS_PARAM = new ScClassParameterElementType
  val TYPE_PARAM_CLAUSE = new ScTypeParamClauseElementType
  val TYPE_PARAM = new ScTypeParamElementType
  val SELF_TYPE = new ScSelfTypeElementElementType
  val PRIMARY_CONSTRUCTOR = new ScPrimaryConstructorElementType

  //Stub element types
  val FILE: IStubFileElementType[_ <: PsiFileStub[_ <: PsiFile]] =
    new ScStubFileElementType

  val CLASS_DEFINITION: ScTemplateDefinitionElementType[ScClass] = ClassDefinition
  val TRAIT_DEFINITION: ScTemplateDefinitionElementType[ScTrait] = TraitDefinition
  val OBJECT_DEFINITION: ScTemplateDefinitionElementType[ScObject] = ObjectDefinition
  val NEW_TEMPLATE: ScTemplateDefinitionElementType[ScNewTemplateDefinition] = NewTemplateDefinition

  val BLOCK_EXPR = new ScCodeBlockElementType with SelfPsiCreator {
    override def createNode(text: CharSequence): ASTNode = new ScBlockExprImpl(text)

    override def createElement(node: ASTNode): PsiElement = PsiUtilCore.NULL_PSI_ELEMENT
  }

  val CONSTRUCTOR = new ScalaElementType("constructor") {
    override def createElement(node: ASTNode) = new ScConstructorImpl(node)
  }

  val COMPOUND_TYPE = new ScalaElementType("compound type") {
    override def createElement(node: ASTNode) = new ScCompoundTypeElementImpl(node)
  }
  val EXISTENTIAL_TYPE = new ScalaElementType("existential type") {
    override def createElement(node: ASTNode) = new ScExistentialTypeElementImpl(node)
  }
  val EXISTENTIAL_CLAUSE = new ScalaElementType("existential clause") {
    override def createElement(node: ASTNode) = new ScExistentialClauseImpl(node)
  }
  val PARAM_TYPE = new ScalaElementType("parameter type") {
    override def createElement(node: ASTNode) = new ScParameterTypeImpl(node)
  }
  val SIMPLE_TYPE = new ScalaElementType("simple type") {
    override def createElement(node: ASTNode) = new ScSimpleTypeElementImpl(node)
  }
  val INFIX_TYPE = new ScalaElementType("infix type") {
    override def createElement(node: ASTNode) = new ScInfixTypeElementImpl(node)
  }
  val TYPE = new ScalaElementType("common type") {
    override def createElement(node: ASTNode) = new ScFunctionalTypeElementImpl(node)
  }
  val TYPES = new ScalaElementType("common type") {
    override def createElement(node: ASTNode) = new ScTypesImpl(node)
  }
  val TYPE_ARGS = new ScalaElementType("type arguments") {
    override def createElement(node: ASTNode) = new ScTypeArgsImpl(node)
  }
  val ANNOT_TYPE = new ScalaElementType("annotation type") {
    override def createElement(node: ASTNode) = new ScAnnotTypeElementImpl(node)
  }
  val WILDCARD_TYPE = new ScalaElementType("wildcard type") {
    override def createElement(node: ASTNode) = new ScWildcardTypeElementImpl(node)
  }
  val TUPLE_TYPE = new ScalaElementType("tuple type") {
    override def createElement(node: ASTNode) = new ScTupleTypeElementImpl(node)
  }
  val TYPE_IN_PARENTHESIS = new ScalaElementType("type in parenthesis") {
    override def createElement(node: ASTNode) = new ScParenthesisedTypeElementImpl(node)
  }
  val TYPE_PROJECTION = new ScalaElementType("type projection") {
    override def createElement(node: ASTNode) = new ScTypeProjectionImpl(node)
  }
  val TYPE_GENERIC_CALL = new ScalaElementType("type generic call") {
    override def createElement(node: ASTNode) = new ScParameterizedTypeElementImpl(node)
  }
  val LITERAL_TYPE = new ScalaElementType("Literal type") {
    override def createElement(node: ASTNode) = new ScLiteralTypeElementImpl(node)
  }
  val SEQUENCE_ARG = new ScalaElementType("sequence argument type") {
    override def createElement(node: ASTNode) = new ScSequenceArgImpl(node)
  }
  val TYPE_VARIABLE = new ScalaElementType("type variable") {
    override def createElement(node: ASTNode) = new ScTypeVariableTypeElementImpl(node)
  }
  val UNIT_EXPR = new ScalaElementType("unit expression") {
    override def createElement(node: ASTNode) = new ScUnitExprImpl(node)
  }
  val REFERENCE = new ScalaElementType("reference") {
    override def createElement(node: ASTNode) = new ScStableCodeReferenceElementImpl(node)
  }

  val CONSTR_EXPR = new ScalaElementType("constructor expression") {
    override def createElement(node: ASTNode) = new ScConstrExprImpl(node)
  }
  val SELF_INVOCATION = new ScalaElementType("self invocation") {
    override def createElement(node: ASTNode) = new ScSelfInvocationImpl(node)
  }

  val NAME_VALUE_PAIR = new ScalaElementType("name value pair") {
    override def createElement(node: ASTNode) = new ScNameValuePairImpl(node)
  }
  val ANNOTATION_EXPR = new ScalaElementType("annotation expression") {
    override def createElement(node: ASTNode) = new ScAnnotationExprImpl(node)
  }
  val LITERAL = new ScalaElementType("Literal") {
    override def createElement(node: ASTNode) = new ScLiteralImpl(node)
  }
  //  String literals
  val INTERPOLATED_STRING_LITERAL = new ScalaElementType("Interpolated String Literal") {
    override def createElement(node: ASTNode) = new ScInterpolatedStringLiteralImpl(node)
  }
  //Not only String, but quasiquote too
  val INTERPOLATED_PREFIX_PATTERN_REFERENCE = new ScalaElementType("Interpolated Prefix Pattern Reference") {
    override def createElement(node: ASTNode) = new ScInterpolatedPrefixReference(node)
  }
  val INTERPOLATED_PREFIX_LITERAL_REFERENCE = new ScalaElementType("Interpolated Prefix Literal Reference") {
    override def createElement(node: ASTNode) = new ScInterpolatedStringPartReference(node)
  }

  /** ***********************************************************************************/
  /** ************************************ EXPRESSIONS **********************************/
  /** ***********************************************************************************/
  /**/
  val PREFIX_EXPR = new ScalaElementType("prefix expression") {
    override def createElement(node: ASTNode) = new ScPrefixExprImpl(node)
  }
  val POSTFIX_EXPR = new ScalaElementType("postfix expression") {
    override def createElement(node: ASTNode) = new ScPostfixExprImpl(node)
  }
  val INFIX_EXPR = new ScalaElementType("infix expression") {
    override def createElement(node: ASTNode) = new ScInfixExprImpl(node)
  }
  val PLACEHOLDER_EXPR = new ScalaElementType("simple expression") {
    override def createElement(node: ASTNode) = new ScUnderscoreSectionImpl(node)
  }

  val PARENT_EXPR = new ScalaElementType("Expression in parentheses") {
    override def createElement(node: ASTNode) = new ScParenthesisedExprImpl(node)
  }
  val METHOD_CALL = new ScalaElementType("Method call") {
    override def createElement(node: ASTNode) = new ScMethodCallImpl(node)
  }
  val REFERENCE_EXPRESSION = new ScalaElementType("Reference expression") {
    override def createElement(node: ASTNode) = new ScReferenceExpressionImpl(node)
  }
  val THIS_REFERENCE = new ScalaElementType("This reference") {
    override def createElement(node: ASTNode) = new ScThisReferenceImpl(node)
  }
  val SUPER_REFERENCE = new ScalaElementType("Super reference") {
    override def createElement(node: ASTNode) = new ScSuperReferenceImpl(node)
  }
  val GENERIC_CALL = new ScalaElementType("Generified call") {
    override def createElement(node: ASTNode) = new ScGenericCallImpl(node)
  }

  val FUNCTION_EXPR = new ScalaElementType("expression") {
    override def createElement(node: ASTNode) = new ScFunctionExprImpl(node)
  }
  val GENERATOR = new ScalaElementType("generator") {
    override def createElement(node: ASTNode) = new ScGeneratorImpl(node)
  }
  val ENUMERATOR = new ScalaElementType("enumerator") {
    override def createElement(node: ASTNode) = new ScEnumeratorImpl(node)
  }
  val ENUMERATORS = new ScalaElementType("enumerator") {
    override def createElement(node: ASTNode) = new ScEnumeratorsImpl(node)
  }
  val GUARD = new ScalaElementType("guard") {
    override def createElement(node: ASTNode) = new ScGuardImpl(node)
  }
  val ARG_EXPRS = new ScalaElementType("arguments of function") {
    override def createElement(node: ASTNode) = new ScArgumentExprListImpl(node)
  }
  val CONSTR_BLOCK = new ScalaElementType("constructor block") {
    override def createElement(node: ASTNode) = new ScConstrBlockImpl(node)
  }
  val BLOCK = new ScalaElementType("block") {
    override def createElement(node: ASTNode) = new ScBlockImpl(node)
  }
  val TUPLE = new ScalaElementType("Tuple") {
    override def createElement(node: ASTNode) = new ScTupleImpl(node)
  }

  /** ****************************** COMPOSITE EXPRESSIONS *****************************/
  val IF_STMT = new ScalaElementType("if statement") {
    override def createElement(node: ASTNode) = new ScIfStmtImpl(node)
  }
  val FOR_STMT = new ScalaElementType("for statement") {
    override def createElement(node: ASTNode) = new ScForStatementImpl(node)
  }
  val DO_STMT = new ScalaElementType("do-while statement") {
    override def createElement(node: ASTNode) = new ScDoStmtImpl(node)
  }
  val TRY_STMT = new ScalaElementType("try statement") {
    override def createElement(node: ASTNode) = new ScTryStmtImpl(node)
  }
  val TRY_BLOCK = new ScalaElementType("try block") {
    override def createElement(node: ASTNode) = new ScTryBlockImpl(node)
  }
  val CATCH_BLOCK = new ScalaElementType("catch block") {
    override def createElement(node: ASTNode) = new ScCatchBlockImpl(node)
  }
  val FINALLY_BLOCK = new ScalaElementType("finally block") {
    override def createElement(node: ASTNode) = new ScFinallyBlockImpl(node)
  }
  val WHILE_STMT = new ScalaElementType("while statement") {
    override def createElement(node: ASTNode) = new ScWhileStmtImpl(node)
  }
  val RETURN_STMT = new ScalaElementType("return statement") {
    override def createElement(node: ASTNode) = new ScReturnStmtImpl(node)
  }
  val THROW_STMT = new ScalaElementType("throw statement") {
    override def createElement(node: ASTNode) = new ScThrowStmtImpl(node)
  }
  val ASSIGN_STMT = new ScalaElementType("assign statement") {
    override def createElement(node: ASTNode) = new ScAssignStmtImpl(node)
  }
  val MATCH_STMT = new ScalaElementType("match statement") {
    override def createElement(node: ASTNode) = new ScMatchStmtImpl(node)
  }
  val TYPED_EXPR_STMT = new ScalaElementType("typed statement") {
    override def createElement(node: ASTNode) = new ScTypedStmtImpl(node)
  }

  /** ***********************************************************************************/
  /** ************************************ PATTERNS *************************************/
  /** ***********************************************************************************/

  val TUPLE_PATTERN = new ScalaElementType("Tuple Pattern") {
    override def createElement(node: ASTNode) = new ScTuplePatternImpl(node)
  }
  val SEQ_WILDCARD = new ScalaElementType("Sequence Wildcard") {
    override def createElement(node: ASTNode) = new ScSeqWildcardImpl(node)
  }
  val CONSTRUCTOR_PATTERN = new ScalaElementType("Constructor Pattern") {
    override def createElement(node: ASTNode) = new ScConstructorPatternImpl(node)
  }
  val PATTERN_ARGS = new ScalaElementType("Pattern arguments") {
    override def createElement(node: ASTNode) = new ScPatternArgumentListImpl(node)
  }
  val INFIX_PATTERN = new ScalaElementType("Infix pattern") {
    override def createElement(node: ASTNode) = new ScInfixPatternImpl(node)
  }
  val NAMING_PATTERN = new ScalaElementType("Binding Pattern") {
    override def createElement(node: ASTNode) = new ScNamingPatternImpl(node)
  }
  val TYPED_PATTERN = new ScalaElementType("Typed Pattern") {
    override def createElement(node: ASTNode) = new ScTypedPatternImpl(node)
  }
  val PATTERN = new ScalaElementType("Composite Pattern") {
    override def createElement(node: ASTNode) = new ScCompositePatternImpl(node)
  }
  val PATTERNS = new ScalaElementType("patterns") {
    override def createElement(node: ASTNode) = new ScPatternsImpl(node)
  }
  val WILDCARD_PATTERN = new ScalaElementType("any sequence") {
    override def createElement(node: ASTNode) = new ScWildcardPatternImpl(node)
  }
  val CASE_CLAUSE = new ScalaElementType("case clause") {
    override def createElement(node: ASTNode) = new ScCaseClauseImpl(node)
  }
  val CASE_CLAUSES = new ScalaElementType("case clauses") {
    override def createElement(node: ASTNode) = new ScCaseClausesImpl(node)
  }
  val LITERAL_PATTERN = new ScalaElementType("literal pattern") {
    override def createElement(node: ASTNode) = new ScLiteralPatternImpl(node)
  }
  val INTERPOLATION_PATTERN = new ScalaElementType("interpolation pattern") {
    override def createElement(node: ASTNode) = new ScInterpolationPatternImpl(node)
  }
  val STABLE_REFERENCE_PATTERN = new ScalaElementType("stable reference pattern") {
    override def createElement(node: ASTNode) = new ScStableReferenceElementPatternImpl(node)
  }
  val PATTERN_IN_PARENTHESIS = new ScalaElementType("pattern in parenthesis") {
    override def createElement(node: ASTNode) = new ScParenthesisedPatternImpl(node)
  }

  /** ************************************ TYPE PATTERNS ********************************/

  val TYPE_PATTERN = new ScalaElementType("Type pattern") {
    override def createElement(node: ASTNode) = new ScTypePatternImpl(node)
  }

  val REFINEMENT = new ScalaElementType("refinement") {
    override def createElement(node: ASTNode) = new ScRefinementImpl(node)
  }

  /** ************************************* XML *************************************/

  val XML_EXPR = new ScalaElementType("Xml expr") {
    override def createElement(node: ASTNode) = new ScXmlExprImpl(node)
  }
  val XML_START_TAG = new ScalaElementType("Xml start tag") {
    override def createElement(node: ASTNode) = new ScXmlStartTagImpl(node)
  }
  val XML_END_TAG = new ScalaElementType("Xml end tag") {
    override def createElement(node: ASTNode) = new ScXmlEndTagImpl(node)
  }
  val XML_EMPTY_TAG = new ScalaElementType("Xml empty tag") {
    override def createElement(node: ASTNode) = new ScXmlEmptyTagImpl(node)
  }
  val XML_PI = new ScalaElementType("Xml proccessing instruction") {
    override def createElement(node: ASTNode) = new ScXmlPIImpl(node)
  }
  val XML_CD_SECT = new ScalaElementType("Xml cdata section") {
    override def createElement(node: ASTNode) = new ScXmlCDSectImpl(node)
  }
  val XML_ATTRIBUTE = new ScalaElementType("Xml attribute") {
    override def createElement(node: ASTNode) = new ScXmlAttributeImpl(node)
  }
  val XML_PATTERN = new ScalaElementType("Xml pattern") {
    override def createElement(node: ASTNode) = new ScXmlPatternImpl(node)
  }
  val XML_COMMENT = new ScalaElementType("Xml comment") {
    override def createElement(node: ASTNode) = new ScXmlCommentImpl(node)
  }
  val XML_ELEMENT = new ScalaElementType("Xml element") {
    override def createElement(node: ASTNode) = new ScXmlElementImpl(node)
  }

  abstract class ScCodeBlockElementType extends IErrorCounterReparseableElementType(
    "block of expressions",
    ScalaLanguage.INSTANCE
  ) with ICompositeElementType {

    import IErrorCounterReparseableElementType._
    import ScalaTokenTypes.{tLBRACE => LeftBrace, tRBRACE => RightBrace}

    @NotNull
    override final def createCompositeNode: ASTNode = createNode(null)

    override final def getErrorsCount(buf: CharSequence,
                                      fileLanguage: Language,
                                      project: Project): Int = {
      val lexer = new ScalaLexer
      lexer.start(buf)
      lexer.getTokenType match {
        case LeftBrace => iterate(1)(lexer)
        case _ => FATAL_ERROR
      }
    }

    @tailrec
    private def iterate(balance: Int)
                       (implicit lexer: ScalaLexer): Int = {
      lexer.advance()
      lexer.getTokenType match {
        case null => balance
        case _ if balance == NO_ERRORS => FATAL_ERROR
        case LeftBrace => iterate(balance + 1)
        case RightBrace => iterate(balance - 1)
        case _ => iterate(balance)
      }
    }
  }

}