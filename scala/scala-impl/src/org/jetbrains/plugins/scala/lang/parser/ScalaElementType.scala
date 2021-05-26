package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.ASTNode
import com.intellij.psi.tree._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScNamingPattern, ScReferencePattern, ScSeqWildcard, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.base._
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals._
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.xml._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterTypeImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScEnumCaseImpl, ScEnumCasesImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.{ScExportStmtImpl, ScImportStmtImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements._
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScImportStmtStub, ScTemplateDefinitionStub}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocResolvableCodeReferenceImpl

sealed abstract class ScalaElementType(debugName: String,
                                       override val isLeftBound: Boolean = true)
  extends IElementType(debugName, ScalaLanguage.INSTANCE)
    with SelfPsiCreator {

  override def createElement(node: ASTNode): ScalaPsiElement

  override final def toString: String = super.toString
}

//noinspection TypeAnnotation
object ScalaElementType {

  //Stub element types

  val IDENTIFIER_LIST = new ScIdListElementType
  val FIELD_ID = new ScFieldIdElementType
  val IMPORT_SELECTOR = new ScImportSelectorElementType
  val IMPORT_SELECTORS = new ScImportSelectorsElementType
  val IMPORT_EXPR = new ScImportExprElementType

  val ImportStatement = new ScImportStmtElementType("ScImportStatement") {

    override protected def createPsi(stub: ScImportStmtStub,
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScImportStmtImpl(stub, nodeType, node, debugName)
  }

  val ExportStatement = new ScImportStmtElementType("ScExportStatement") {

    override protected def createPsi(stub: ScImportStmtStub,
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScExportStmtImpl(stub, nodeType, node, debugName)
  }

  val Extension = new ScExtensionElementType
  val VALUE_DECLARATION: ScPropertyElementType[ScValueDeclaration] = ValueDeclaration
  val PATTERN_DEFINITION: ScPropertyElementType[ScPatternDefinition] = ValueDefinition
  val VARIABLE_DECLARATION: ScPropertyElementType[ScVariableDeclaration] = VariableDeclaration
  val VARIABLE_DEFINITION: ScPropertyElementType[ScVariableDefinition] = VariableDefinition
  val FUNCTION_DECLARATION: ScFunctionElementType[ScFunctionDeclaration] = FunctionDeclaration
  val FUNCTION_DEFINITION: ScFunctionElementType[ScFunctionDefinition] = FunctionDefinition
  val MACRO_DEFINITION: ScFunctionElementType[ScMacroDefinition] = MacroDefinition
  val GIVEN_ALIAS: ScFunctionElementType[ScGivenAlias] = GivenAlias
  val TYPE_DECLARATION = new ScTypeAliasDeclarationElementType
  val PATTERN_LIST = new ScPatternListElementType
  val TYPE_DEFINITION = new ScTypeAliasDefinitionElementType
  val EARLY_DEFINITIONS = new ScEarlyDefinitionsElementType
  val MODIFIERS = new ScModifiersElementType("modifiers")
  val ACCESS_MODIFIER = new ScAccessModifierElementType
  val ANNOTATION = new ScAnnotationElementType
  val ANNOTATIONS = new ScAnnotationsElementType
  val PACKAGING: ScPackagingElementType.type = ScPackagingElementType
  val EXTENDS_BLOCK = new ScExtendsBlockElementType
  val TEMPLATE_PARENTS = new ScTemplateParentsElementType
  val TEMPLATE_DERIVES = new ScTemplateDerivesElementType
  val TEMPLATE_BODY = new ScTemplateBodyElementType
  val PARAM = new signatures.ScParameterElementType
  val PARAM_CLAUSE = new signatures.ScParamClauseElementType
  val PARAM_CLAUSES = new signatures.ScParamClausesElementType
  val CLASS_PARAM = new signatures.ScClassParameterElementType
  val TYPE_PARAM_CLAUSE = new ScTypeParamClauseElementType
  val TYPE_PARAM = new ScTypeParamElementType
  val SELF_TYPE = new ScSelfTypeElementElementType
  val PRIMARY_CONSTRUCTOR = new ScPrimaryConstructorElementType

  val ClassDefinition = new ScTemplateDefinitionElementType[ScClass]("ScClass") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScClass],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScClassImpl(stub, nodeType, node, debugName)
  }

  val TraitDefinition = new ScTemplateDefinitionElementType[ScTrait]("ScTrait") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScTrait],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScTraitImpl(stub, nodeType, node, debugName)
  }

  val ObjectDefinition = new ScTemplateDefinitionElementType[ScObject]("ScObject") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScObject],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScObjectImpl(stub, nodeType, node, debugName)
  }

  val EnumDefinition = new ScTemplateDefinitionElementType[ScEnum]("ScEnum") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScEnum],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScEnumImpl(stub, nodeType, node, debugName)
  }

  val EnumCase = new ScTemplateDefinitionElementType[ScEnumCase]("ScEnumCase") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScEnumCase],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScEnumCaseImpl(stub, nodeType, node, debugName)
  }

  val EnumCases = new ScalaElementType("ScEnumCases") {
    override def createElement(node: ASTNode) = new ScEnumCasesImpl(node, toString)
  }

  val NewTemplate = new ScTemplateDefinitionElementType[ScNewTemplateDefinition]("ScNewTemplateDefinition") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScNewTemplateDefinition],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScNewTemplateDefinitionImpl(stub, nodeType, node, debugName)
  }

  val GivenDefinition = new ScTemplateDefinitionElementType[ScGivenDefinition]("ScGivenDefinition") {
    override protected def createPsi(stub: ScTemplateDefinitionStub[ScGivenDefinition],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String): ScGivenDefinition = {
      new ScGivenDefinitionImpl(stub, nodeType, node, debugName)
    }
  }

  val REFERENCE_PATTERN: ScBindingPatternElementType[ScReferencePattern] = ScReferencePatternElementType
  val TYPED_PATTERN: ScBindingPatternElementType[ScTypedPattern] = ScTypedPatternElementType
  val NAMING_PATTERN: ScBindingPatternElementType[ScNamingPattern] = ScNamingPatternElementType
  val SEQ_WILDCARD: ScBindingPatternElementType[ScSeqWildcard] = ScSeqWildcardPatternElementType

  /** ***********************************************************************************/
  /** ****************************** DEFINITION PARTS ***********************************/
  /** ***********************************************************************************/
  val CONSTRUCTOR: ScalaElementType = new ScalaElementType("constructor") {
    override def createElement(node: ASTNode) = new ScConstructorInvocationImpl(node)
  }
  val PARAM_TYPE: ScalaElementType = new ScalaElementType("parameter type") {
    override def createElement(node: ASTNode) = new ScParameterTypeImpl(node)
  }
  val SEQUENCE_ARG: ScalaElementType = new ScalaElementType("sequence argument type") {
    override def createElement(node: ASTNode) = new ScSequenceArgImpl(node)
  }
  val REFERENCE: ScalaElementType = new ScalaElementType("reference") {
    override def createElement(node: ASTNode) = new ScStableCodeReferenceImpl(node)
  }
  /** NOTE: only created to be used from
   *  [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory#createDocReferenceFromText]]
   *  to create a syntetic reference from doc
   */
  val DOC_REFERENCE: ScalaElementType = new ScalaElementType("doc reference") {
    override def createElement(node: ASTNode) = new ScDocResolvableCodeReferenceImpl(node)
  }
  val NAME_VALUE_PAIR: ScalaElementType = new ScalaElementType("name value pair") {
    override def createElement(node: ASTNode) = new ScNameValuePairImpl(node)
  }
  val ANNOTATION_EXPR: ScalaElementType = new ScalaElementType("annotation expression") {
    override def createElement(node: ASTNode) = new ScAnnotationExprImpl(node)
  }
  val END_STMT: ScalaElementType = new ScalaElementType("end") {
    override def createElement(node: ASTNode): ScalaPsiElement = new ScEndImpl(node)
  }

  /** ***********************************************************************************/
  /** ****************************** TYPES **********************************************/
  /** ***********************************************************************************/

  sealed abstract class ScTypeElementType(debugName: String) extends ScalaElementType(debugName) {
    override def createElement(node: ASTNode): ScTypeElement
  }

  val COMPOUND_TYPE: ScTypeElementType = new ScTypeElementType("compound type") {
    override def createElement(node: ASTNode) = new ScCompoundTypeElementImpl(node)
  }
  val EXISTENTIAL_TYPE: ScTypeElementType = new ScTypeElementType("existential type") {
    override def createElement(node: ASTNode) = new ScExistentialTypeElementImpl(node)
  }
  val SIMPLE_TYPE: ScTypeElementType = new ScTypeElementType("simple type") {
    override def createElement(node: ASTNode) = new ScSimpleTypeElementImpl(node)
  }
  val INFIX_TYPE: ScTypeElementType = new ScTypeElementType("infix type") {
    override def createElement(node: ASTNode) = new ScInfixTypeElementImpl(node)
  }
  val TYPE: ScTypeElementType = new ScTypeElementType("common type") {
    override def createElement(node: ASTNode) = new ScFunctionalTypeElementImpl(node)
  }
  val ANNOT_TYPE: ScTypeElementType = new ScTypeElementType("annotation type") {
    override def createElement(node: ASTNode) = new ScAnnotTypeElementImpl(node)
  }
  val WILDCARD_TYPE: ScTypeElementType = new ScTypeElementType("wildcard type") {
    override def createElement(node: ASTNode) = new ScWildcardTypeElementImpl(node)
  }
  val TUPLE_TYPE: ScTypeElementType = new ScTypeElementType("tuple type") {
    override def createElement(node: ASTNode) = new ScTupleTypeElementImpl(node)
  }
  val TYPE_IN_PARENTHESIS: ScTypeElementType = new ScTypeElementType("type in parenthesis") {
    override def createElement(node: ASTNode) = new ScParenthesisedTypeElementImpl(node)
  }
  val TYPE_PROJECTION: ScTypeElementType = new ScTypeElementType("type projection") {
    override def createElement(node: ASTNode) = new ScTypeProjectionImpl(node)
  }
  val TYPE_GENERIC_CALL: ScTypeElementType = new ScTypeElementType("type generic call") {
    override def createElement(node: ASTNode) = new ScParameterizedTypeElementImpl(node)
  }
  val LITERAL_TYPE: ScTypeElementType = new ScTypeElementType("literal type") {
    override def createElement(node: ASTNode) = new ScLiteralTypeElementImpl(node)
  }
  val TYPE_VARIABLE: ScTypeElementType = new ScTypeElementType("type variable") {
    override def createElement(node: ASTNode) = new ScTypeVariableTypeElementImpl(node)
  }
  val SPLICED_BLOCK_TYPE: ScTypeElementType = new ScTypeElementType("spliced block") {
    override def createElement(node: ASTNode) = new ScSplicedBlockImpl(node)
  }
  val TYPE_LAMBDA: ScTypeElementType = new ScTypeElementType("type lambda") {
    override def createElement(node: ASTNode): ScTypeElement = new ScTypeLambdaTypeElementImpl(node)
  }
  val MATCH_TYPE: ScTypeElementType = new ScTypeElementType("match type") {
    override def createElement(node: ASTNode): ScTypeElement = new ScMatchTypeElementImpl(node)
  }
  val POLY_FUNCTION_TYPE: ScTypeElementType = new ScTypeElementType("poly function type") {
    override def createElement(node: ASTNode): ScTypeElement = new ScPolyFunctionTypeElementImpl(node)
  }
  val DEPENDENT_FUNCTION_TYPE: ScTypeElementType = new ScTypeElementType("dependent function type") {
    override def createElement(node: ASTNode): ScTypeElement = new ScDependentFunctionTypeElementImpl(node)
  }

  /** ***********************************************************************************/
  /** ************************************ TYPE PARTS ***********************************/
  /** ***********************************************************************************/
  val TYPE_ARGS: ScalaElementType = new ScalaElementType("type arguments") {
    override def createElement(node: ASTNode) = new ScTypeArgsImpl(node)
  }
  val EXISTENTIAL_CLAUSE: ScalaElementType = new ScalaElementType("existential clause") {
    override def createElement(node: ASTNode) = new ScExistentialClauseImpl(node)
  }
  val TYPES: ScalaElementType = new ScalaElementType("common type") {
    override def createElement(node: ASTNode) = new ScTypesImpl(node)
  }

  val TYPE_CASE_CLAUSES: ScalaElementType = new ScalaElementType("match type cases") {
    override def createElement(node: ASTNode): ScalaPsiElement = new ScMatchTypeCasesImpl(node)
  }

  val TYPE_CASE_CLAUSE: ScalaElementType = new ScalaElementType("match type case") {
    override def createElement(node: ASTNode): ScalaPsiElement = new ScMatchTypeCaseImpl(node)
  }

  /** ***********************************************************************************/
  /** ************************************ EXPRESSIONS **********************************/
  /** ***********************************************************************************/

  sealed abstract class ScExpressionElementType(debugName: String) extends ScalaElementType(debugName) {

    override def createElement(node: ASTNode): ScExpression
  }

  val PREFIX_EXPR: ScExpressionElementType = new ScExpressionElementType("prefix expression") {
    override def createElement(node: ASTNode) = new ScPrefixExprImpl(node)
  }
  val POSTFIX_EXPR: ScExpressionElementType = new ScExpressionElementType("postfix expression") {
    override def createElement(node: ASTNode) = new ScPostfixExprImpl(node)
  }
  val INFIX_EXPR: ScExpressionElementType = new ScExpressionElementType("infix expression") {
    override def createElement(node: ASTNode) = new ScInfixExprImpl(node)
  }
  val PLACEHOLDER_EXPR: ScExpressionElementType = new ScExpressionElementType("simple expression") {
    override def createElement(node: ASTNode) = new ScUnderscoreSectionImpl(node)
  }
  val PARENT_EXPR: ScExpressionElementType = new ScExpressionElementType("Expression in parentheses") {
    override def createElement(node: ASTNode) = new ScParenthesisedExprImpl(node)
  }
  val METHOD_CALL: ScExpressionElementType = new ScExpressionElementType("Method call") {
    override def createElement(node: ASTNode) = new ScMethodCallImpl(node)
  }
  val REFERENCE_EXPRESSION: ScExpressionElementType = new ScExpressionElementType("Reference expression") {
    override def createElement(node: ASTNode) = new ScReferenceExpressionImpl(node)
  }
  val THIS_REFERENCE: ScExpressionElementType = new ScExpressionElementType("This reference") {
    override def createElement(node: ASTNode) = new ScThisReferenceImpl(node)
  }
  val SUPER_REFERENCE: ScExpressionElementType = new ScExpressionElementType("Super reference") {
    override def createElement(node: ASTNode) = new ScSuperReferenceImpl(node)
  }
  val GENERIC_CALL: ScExpressionElementType = new ScExpressionElementType("Generified call") {
    override def createElement(node: ASTNode) = new ScGenericCallImpl(node)
  }
  val FUNCTION_EXPR: ScExpressionElementType = new ScExpressionElementType("expression") {
    override def createElement(node: ASTNode) = new ScFunctionExprImpl(node)
  }
  val POLY_FUNCTION_EXPR: ScExpressionElementType = new ScExpressionElementType("expression") {
    override def createElement(node: ASTNode) = new ScPolyFunctionExprImpl(node)
  }
  val BLOCK: ScExpressionElementType = new ScExpressionElementType("block") {
    override def createElement(node: ASTNode) = new ScBlockImpl(node)
  }
  val SPLICED_BLOCK_EXPR: ScExpressionElementType = new ScExpressionElementType("spliced block") {
    override def createElement(node: ASTNode) = new ScSplicedBlockImpl(node)
  }
  val QUOTED_BLOCK: ScExpressionElementType = new ScExpressionElementType("quoted block") {
    override def createElement(node: ASTNode) = new ScQuotedBlockImpl(node)
  }
  val QUOTED_TYPE: ScExpressionElementType = new ScExpressionElementType("quoted type") {
    override def createElement(node: ASTNode): ScExpression = new ScQuotedTypeImpl(node)
  }
  val TUPLE: ScExpressionElementType = new ScExpressionElementType("Tuple") {
    override def createElement(node: ASTNode) = new ScTupleImpl(node)
  }
  val UNIT_EXPR: ScExpressionElementType = new ScExpressionElementType("unit expression") {
    override def createElement(node: ASTNode) = new ScUnitExprImpl(node)
  }
  val CONSTR_BLOCK_EXPR: ScExpressionElementType = new ScExpressionElementType("constructor block expression") {
    override def createElement(node: ASTNode) = new ScConstrBlockExprImpl(node)
  }
  val SELF_INVOCATION: ScExpressionElementType = new ScExpressionElementType("self invocation") {
    override def createElement(node: ASTNode) = new ScSelfInvocationImpl(node)
  }

  object NullLiteral extends ScExpressionElementType("NullLiteral") {
    override def createElement(node: ASTNode) = new ScNullLiteralImpl(node, toString)
  }

  object LongLiteral extends ScExpressionElementType("LongLiteral") {
    override def createElement(node: ASTNode) = new ScLongLiteralImpl(node, toString)
  }

  object IntegerLiteral extends ScExpressionElementType("IntegerLiteral") {
    override def createElement(node: ASTNode) = new ScIntegerLiteralImpl(node, toString) // but a conversion exists to narrower types in case range fits
  }

  object DoubleLiteral extends ScExpressionElementType("DoubleLiteral") {
    override def createElement(node: ASTNode) = new ScDoubleLiteralImpl(node, toString)
  }

  object FloatLiteral extends ScExpressionElementType("FloatLiteral") {
    override def createElement(node: ASTNode) = new ScFloatLiteralImpl(node, toString)
  }

  object BooleanLiteral extends ScExpressionElementType("BooleanLiteral") {
    override def createElement(node: ASTNode) = new ScBooleanLiteralImpl(node, toString)
  }

  object SymbolLiteral extends ScExpressionElementType("SymbolLiteral") {
    override def createElement(node: ASTNode) = new ScSymbolLiteralImpl(node, toString)
  }

  object CharLiteral extends ScExpressionElementType("CharLiteral") {
    override def createElement(node: ASTNode) = new ScCharLiteralImpl(node, toString)
  }

  object StringLiteral extends ScExpressionElementType("StringLiteral") {
    override def createElement(node: ASTNode) = new ScStringLiteralImpl(node, toString)
  }

  object InterpolatedString extends ScExpressionElementType("InterpolatedStringLiteral") {
    override def createElement(node: ASTNode) = new ScInterpolatedStringLiteralImpl(node, toString)
  }

  val INTERPOLATED_PREFIX_LITERAL_REFERENCE: ScExpressionElementType = new ScExpressionElementType("Interpolated Prefix Literal Reference") {
    override def createElement(node: ASTNode) = new ScInterpolatedExpressionPrefix(node)
  }

  /** ****************************** COMPOSITE EXPRESSIONS *****************************/
  val IF_STMT: ScExpressionElementType = new ScExpressionElementType("if statement") {
    override def createElement(node: ASTNode) = new ScIfImpl(node)
  }
  val FOR_STMT: ScExpressionElementType = new ScExpressionElementType("for statement") {
    override def createElement(node: ASTNode) = new ScForImpl(node)
  }
  val DO_STMT: ScExpressionElementType = new ScExpressionElementType("do-while statement") {
    override def createElement(node: ASTNode) = new ScDoImpl(node)
  }
  val TRY_STMT: ScExpressionElementType = new ScExpressionElementType("try statement") {
    override def createElement(node: ASTNode) = new ScTryImpl(node)
  }
  val CATCH_BLOCK: ScExpressionElementType = new ScExpressionElementType("catch block") {
    override def createElement(node: ASTNode) = new ScCatchBlockImpl(node)
  }
  val FINALLY_BLOCK: ScExpressionElementType = new ScExpressionElementType("finally block") {
    override def createElement(node: ASTNode) = new ScFinallyBlockImpl(node)
  }
  val WHILE_STMT: ScExpressionElementType = new ScExpressionElementType("while statement") {
    override def createElement(node: ASTNode) = new ScWhileImpl(node)
  }
  val RETURN_STMT: ScExpressionElementType = new ScExpressionElementType("return statement") {
    override def createElement(node: ASTNode) = new ScReturnImpl(node)
  }
  val THROW_STMT: ScExpressionElementType = new ScExpressionElementType("throw statement") {
    override def createElement(node: ASTNode) = new ScThrowImpl(node)
  }
  val ASSIGN_STMT: ScExpressionElementType = new ScExpressionElementType("assign statement") {
    override def createElement(node: ASTNode) = new ScAssignmentImpl(node)
  }
  val MATCH_STMT: ScExpressionElementType = new ScExpressionElementType("match statement") {
    override def createElement(node: ASTNode) = new ScMatchImpl(node)
  }
  val TYPED_EXPR_STMT: ScExpressionElementType = new ScExpressionElementType("typed statement") {
    override def createElement(node: ASTNode) = new ScTypedExpressionImpl(node)
  }

  /** ***********************************************************************************/
  /** ********************************* Expression parts ********************************/
  /** ***********************************************************************************/

  val GENERATOR: ScalaElementType = new ScalaElementType("generator") {
    override def createElement(node: ASTNode) = new ScGeneratorImpl(node)
  }
  val FOR_BINDING: ScalaElementType = new ScalaElementType("for binding") {
    override def createElement(node: ASTNode) = new ScForBindingImpl(node)
  }
  val ENUMERATORS: ScalaElementType = new ScalaElementType("enumerators") {
    override def createElement(node: ASTNode) = new ScEnumeratorsImpl(node)
  }
  val GUARD: ScalaElementType = new ScalaElementType("guard") {
    override def createElement(node: ASTNode) = new ScGuardImpl(node)
  }
  val ARG_EXPRS: ScalaElementType = new ScalaElementType("arguments of function") {
    override def createElement(node: ASTNode) = new ScArgumentExprListImpl(node)
  }
  //Not only String, but quasiquote too
  val INTERPOLATED_PREFIX_PATTERN_REFERENCE: ScalaElementType = new ScalaElementType("Interpolated Prefix Pattern Reference") {
    override def createElement(node: ASTNode) = new ScInterpolatedPatternPrefix(node)
  }

  /** ***********************************************************************************/
  /** ************************************ PATTERNS *************************************/
  /** ***********************************************************************************/

  val TUPLE_PATTERN: ScalaElementType = new ScalaElementType("Tuple Pattern") {
    override def createElement(node: ASTNode) = new ScTuplePatternImpl(node)
  }
  val CONSTRUCTOR_PATTERN: ScalaElementType = new ScalaElementType("Constructor Pattern") {
    override def createElement(node: ASTNode) = new ScConstructorPatternImpl(node)
  }
  val PATTERN_ARGS: ScalaElementType = new ScalaElementType("Pattern arguments") {
    override def createElement(node: ASTNode) = new ScPatternArgumentListImpl(node)
  }
  val INFIX_PATTERN: ScalaElementType = new ScalaElementType("Infix pattern") {
    override def createElement(node: ASTNode) = new ScInfixPatternImpl(node)
  }
  val PATTERN: ScalaElementType = new ScalaElementType("Composite Pattern") {
    override def createElement(node: ASTNode) = new ScCompositePatternImpl(node)
  }
  val PATTERNS: ScalaElementType = new ScalaElementType("patterns") {
    override def createElement(node: ASTNode) = new ScPatternsImpl(node)
  }
  val WILDCARD_PATTERN: ScalaElementType = new ScalaElementType("any sequence") {
    override def createElement(node: ASTNode) = new ScWildcardPatternImpl(node)
  }
  val CASE_CLAUSE: ScalaElementType = new ScalaElementType("case clause") {
    override def createElement(node: ASTNode) = new ScCaseClauseImpl(node)
  }
  val CASE_CLAUSES: ScalaElementType = new ScalaElementType("case clauses") {
    override def createElement(node: ASTNode) = new ScCaseClausesImpl(node)
  }
  val LITERAL_PATTERN: ScalaElementType = new ScalaElementType("literal pattern") {
    override def createElement(node: ASTNode) = new ScLiteralPatternImpl(node)
  }
  val INTERPOLATION_PATTERN: ScalaElementType = new ScalaElementType("interpolation pattern") {
    override def createElement(node: ASTNode) = new ScInterpolationPatternImpl(node)
  }
  object StableReferencePattern extends ScalaElementType("StableElementPattern") {
    override def createElement(node: ASTNode) = new ScStableReferencePatternImpl(node, toString)
  }
  val PATTERN_IN_PARENTHESIS: ScalaElementType = new ScalaElementType("pattern in parenthesis") {
    override def createElement(node: ASTNode) = new ScParenthesisedPatternImpl(node)
  }
  val GIVEN_PATTERN: ScalaElementType = new ScalaElementType("given pattern") {
    override def createElement(node: ASTNode): ScalaPsiElement = new ScGivenPatternImpl(node)
  }
  val SCALA3_TYPED_PATTERN: ScalaElementType = new ScalaElementType("Scala 3 Typed Pattern") {
    override def createElement(node: ASTNode): ScalaPsiElement = new Sc3TypedPatternImpl(node)
  }

  /** ************************************ TYPE PATTERNS ********************************/

  val TYPE_PATTERN: ScalaElementType = new ScalaElementType("Type pattern") {
    override def createElement(node: ASTNode) = new ScTypePatternImpl(node)
  }

  val REFINEMENT: ScalaElementType = new ScalaElementType("refinement") {
    override def createElement(node: ASTNode) = new ScRefinementImpl(node)
  }

  /** ************************************* XML *************************************/

  val XML_EXPR: ScalaElementType = new ScalaElementType("Xml expr") {
    override def createElement(node: ASTNode) = new ScXmlExprImpl(node)
  }
  val XML_START_TAG: ScalaElementType = new ScalaElementType("Xml start tag") {
    override def createElement(node: ASTNode) = new ScXmlStartTagImpl(node)
  }
  val XML_END_TAG: ScalaElementType = new ScalaElementType("Xml end tag") {
    override def createElement(node: ASTNode) = new ScXmlEndTagImpl(node)
  }
  val XML_EMPTY_TAG: ScalaElementType = new ScalaElementType("Xml empty tag") {
    override def createElement(node: ASTNode) = new ScXmlEmptyTagImpl(node)
  }
  val XML_PI: ScalaElementType = new ScalaElementType("Xml proccessing instruction") {
    override def createElement(node: ASTNode) = new ScXmlPIImpl(node)
  }
  val XML_CD_SECT: ScalaElementType = new ScalaElementType("Xml cdata section") {
    override def createElement(node: ASTNode) = new ScXmlCDSectImpl(node)
  }
  val XML_ATTRIBUTE: ScalaElementType = new ScalaElementType("Xml attribute") {
    override def createElement(node: ASTNode) = new ScXmlAttributeImpl(node)
  }
  val XML_PATTERN: ScalaElementType = new ScalaElementType("Xml pattern") {
    override def createElement(node: ASTNode) = new ScXmlPatternImpl(node)
  }
  val XML_COMMENT: ScalaElementType = new ScalaElementType("Xml comment") {
    override def createElement(node: ASTNode) = new ScXmlCommentImpl(node)
  }
  val XML_ELEMENT: ScalaElementType = new ScalaElementType("Xml element") {
    override def createElement(node: ASTNode) = new ScXmlElementImpl(node)
  }

  val REFINED_TYPE: ScalaElementType = new ScalaElementType("Dotty refined type") {
    override def createElement(node: ASTNode): ScalaPsiElement = ???
  }

  val WITH_TYPE: ScalaElementType = new ScalaElementType("Dotty with type") {
    override def createElement(node: ASTNode): ScalaPsiElement = ???
  }

  val TYPE_ARGUMENT_NAME: ScalaElementType = new ScalaElementType("Dotty type argument name") {
    override def createElement(node: ASTNode): ScalaPsiElement = ???
  }
}