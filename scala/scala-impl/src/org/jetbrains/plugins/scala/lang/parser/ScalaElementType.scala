package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.tree._
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScNamingPattern, ScReferencePattern, ScSeqWildcardPattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScEnumClassCaseImpl, ScEnumSingletonCaseImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements._

sealed class ScalaElementType(debugName: String,
                              override val isLeftBound: Boolean = true)
  extends IElementType(debugName, ScalaLanguage.INSTANCE) {

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

  val ImportStatement = new ScImportStmtElementType
  val ExportStatement = new ScExportStmtElementType

  val EXTENSION = new ScExtensionElementType
  val VALUE_DECLARATION: ScPropertyElementType[ScValueDeclaration] = ValueDeclaration
  val PATTERN_DEFINITION: ScPropertyElementType[ScPatternDefinition] = ValueDefinition
  val VARIABLE_DECLARATION: ScPropertyElementType[ScVariableDeclaration] = VariableDeclaration
  val VARIABLE_DEFINITION: ScPropertyElementType[ScVariableDefinition] = VariableDefinition
  val FUNCTION_DECLARATION: ScFunctionElementType[ScFunctionDeclaration] = FunctionDeclaration
  val FUNCTION_DEFINITION: ScFunctionElementType[ScFunctionDefinition] = FunctionDefinition
  val MACRO_DEFINITION: ScFunctionElementType[ScMacroDefinition] = MacroDefinition
  val GIVEN_ALIAS_DECLARATION: ScFunctionElementType[ScGivenAliasDeclaration] = GivenAliasDeclaration
  val GIVEN_ALIAS_DEFINITION: ScFunctionElementType[ScGivenAliasDefinition] = GivenAliasDefinition
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
  val DERIVES_CLAUSE = new ScDerivesClauseElementType
  val TEMPLATE_BODY = new ScTemplateBodyElementType
  val EXTENSION_BODY = new ScExtensionBodyElementType
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

  val EnumDefinition = new ScTemplateDefinitionElementType[ScClass]("ScEnum") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScClass],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScEnumImpl(stub, nodeType, node, debugName)
  }

  val EnumClassCase = new ScTemplateDefinitionElementType[ScClass]("ScEnumClassCase") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScClass],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScEnumClassCaseImpl(stub, nodeType, node, debugName)
  }

  val EnumSingletonCase = new ScTemplateDefinitionElementType[ScObject]("ScEnumSingletonCase") {

    override protected def createPsi(stub: ScTemplateDefinitionStub[ScObject],
                                     nodeType: this.type,
                                     node: ASTNode,
                                     debugName: String) =
      new ScEnumSingletonCaseImpl(stub, nodeType, node, debugName)
  }

  val EnumCases = ScEnumCasesElementType

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
  val SEQ_WILDCARD_PATTERN: ScBindingPatternElementType[ScSeqWildcardPattern] = ScSeqWildcardPatternElementType

  /** ********************************************************************************** */
  /** ****************************** DEFINITION PARTS ********************************** */
  /** ********************************************************************************** */
  val CONSTRUCTOR: ScalaElementType = new ScalaElementType("constructor")
  val PARAM_TYPE: ScalaElementType = new ScalaElementType("parameter type")
  val SEQUENCE_ARG: ScalaElementType = new ScalaElementType("sequence argument type")
  val REFERENCE: ScalaElementType = new ScalaElementType("reference")
  /** NOTE: only created to be used from
   * [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory#createDocReferenceFromText]]
   * to create a syntetic reference from doc
   */
  val DOC_REFERENCE: ScalaElementType = new ScalaElementType("doc reference")
  val ANNOTATION_EXPR: ScalaElementType = new ScalaElementType("annotation expression")
  val END_STMT: ScalaElementType = new ScalaElementType("end")

  /** ********************************************************************************** */
  /** ****************************** TYPES ********************************************* */
  /** ********************************************************************************** */

  sealed class ScTypeElementType(debugName: String) extends ScalaElementType(debugName)

  val COMPOUND_TYPE: ScTypeElementType = new ScTypeElementType("compound type")
  val EXISTENTIAL_TYPE: ScTypeElementType = new ScTypeElementType("existential type")
  val SIMPLE_TYPE: ScTypeElementType = new ScTypeElementType("simple type")
  val INFIX_TYPE: ScTypeElementType = new ScTypeElementType("infix type")
  val TYPE: ScTypeElementType = new ScTypeElementType("common type")
  val ANNOT_TYPE: ScTypeElementType = new ScTypeElementType("annotation type")
  val WILDCARD_TYPE: ScTypeElementType = new ScTypeElementType("wildcard type")
  val TUPLE_TYPE: ScTypeElementType = new ScTypeElementType("tuple type")
  val NAMED_TUPLE_TYPE: ScTypeElementType = new ScTypeElementType("named tuple type")
  val TYPE_IN_PARENTHESIS: ScTypeElementType = new ScTypeElementType("type in parenthesis")
  val TYPE_PROJECTION: ScTypeElementType = new ScTypeElementType("type projection")
  val TYPE_GENERIC_CALL: ScTypeElementType = new ScTypeElementType("type generic call")
  val LITERAL_TYPE: ScTypeElementType = new ScTypeElementType("literal type")
  val TYPE_VARIABLE: ScTypeElementType = new ScTypeElementType("type variable")
  val SPLICED_BLOCK_TYPE: ScTypeElementType = new ScTypeElementType("spliced block")
  val SPLICED_PATTERN_EXPR: ScalaElementType = new ScalaElementType("spliced pattern expression")
  val TYPE_LAMBDA: ScTypeElementType = new ScTypeElementType("type lambda")
  val MATCH_TYPE: ScTypeElementType = new ScTypeElementType("match type")
  val POLY_FUNCTION_TYPE: ScTypeElementType = new ScTypeElementType("poly function type")
  val DEPENDENT_FUNCTION_TYPE: ScTypeElementType = new ScTypeElementType("dependent function type")

  /** ********************************************************************************** */
  /** ************************************ TYPE PARTS ********************************** */
  /** ********************************************************************************** */
  val TYPE_ARGS: ScalaElementType = new ScalaElementType("type arguments")
  val EXISTENTIAL_CLAUSE: ScalaElementType = new ScalaElementType("existential clause")
  val TYPES: ScalaElementType = new ScalaElementType("common type")

  val TYPE_CASE_CLAUSES: ScalaElementType = new ScalaElementType("match type cases")

  val TYPE_CASE_CLAUSE: ScalaElementType = new ScalaElementType("match type case")
  val CONTEXT_BOUND: ScalaElementType = new ScalaElementType("context bound")
  val NAMED_TUPLE_TYPE_COMPONENT: ScalaElementType = new ScalaElementType("named tuple type component")

  /** ********************************************************************************** */
  /** ************************************ EXPRESSIONS ********************************* */
  /** ********************************************************************************** */

  sealed class ScExpressionElementType(debugName: String) extends ScalaElementType(debugName)

  val PREFIX_EXPR: ScExpressionElementType = new ScExpressionElementType("prefix expression")
  val POSTFIX_EXPR: ScExpressionElementType = new ScExpressionElementType("postfix expression")
  val INFIX_EXPR: ScExpressionElementType = new ScExpressionElementType("infix expression")
  val PLACEHOLDER_EXPR: ScExpressionElementType = new ScExpressionElementType("simple expression")
  val PARENT_EXPR: ScExpressionElementType = new ScExpressionElementType("Expression in parentheses")
  val METHOD_CALL: ScExpressionElementType = new ScExpressionElementType("Method call")
  val REFERENCE_EXPRESSION: ScExpressionElementType = new ScExpressionElementType("Reference expression")
  val THIS_REFERENCE: ScExpressionElementType = new ScExpressionElementType("This reference")
  val SUPER_REFERENCE: ScExpressionElementType = new ScExpressionElementType("Super reference")
  val GENERIC_CALL: ScExpressionElementType = new ScExpressionElementType("Generified call")
  val FUNCTION_EXPR: ScExpressionElementType = new ScExpressionElementType("expression")
  val POLY_FUNCTION_EXPR: ScExpressionElementType = new ScExpressionElementType("expression")
  val BLOCK: ScExpressionElementType = new ScExpressionElementType("block")
  val SPLICED_BLOCK_EXPR: ScExpressionElementType = new ScExpressionElementType("spliced block")
  val QUOTED_BLOCK: ScExpressionElementType = new ScExpressionElementType("quoted block")
  val QUOTED_TYPE: ScExpressionElementType = new ScExpressionElementType("quoted type")
  val TUPLE: ScExpressionElementType = new ScExpressionElementType("Tuple")
  val NAMED_TUPLE: ScExpressionElementType = new ScExpressionElementType("Named Tuple")
  val UNIT_EXPR: ScExpressionElementType = new ScExpressionElementType("unit expression")
  val CONSTR_BLOCK_EXPR: ScExpressionElementType = new ScExpressionElementType("constructor block expression")
  val SELF_INVOCATION: ScExpressionElementType = new ScExpressionElementType("self invocation")

  object NullLiteral extends ScExpressionElementType("NullLiteral")
  object LongLiteral extends ScExpressionElementType("LongLiteral")
  object IntegerLiteral extends ScExpressionElementType("IntegerLiteral")
  object DoubleLiteral extends ScExpressionElementType("DoubleLiteral")
  object FloatLiteral extends ScExpressionElementType("FloatLiteral")
  object BooleanLiteral extends ScExpressionElementType("BooleanLiteral")
  object SymbolLiteral extends ScExpressionElementType("SymbolLiteral")
  object CharLiteral extends ScExpressionElementType("CharLiteral")
  object StringLiteral extends ScExpressionElementType("StringLiteral")
  object InterpolatedString extends ScExpressionElementType("InterpolatedStringLiteral")

  val INTERPOLATED_PREFIX_LITERAL_REFERENCE: ScExpressionElementType = new ScExpressionElementType("Interpolated Prefix Literal Reference")

  /** ****************************** COMPOSITE EXPRESSIONS **************************** */
  val IF_STMT: ScExpressionElementType = new ScExpressionElementType("if statement")
  val FOR_STMT: ScExpressionElementType = new ScExpressionElementType("for statement")
  val DO_STMT: ScExpressionElementType = new ScExpressionElementType("do-while statement")
  val TRY_STMT: ScExpressionElementType = new ScExpressionElementType("try statement")
  val CATCH_BLOCK: ScExpressionElementType = new ScExpressionElementType("catch block")
  val FINALLY_BLOCK: ScExpressionElementType = new ScExpressionElementType("finally block")
  val WHILE_STMT: ScExpressionElementType = new ScExpressionElementType("while statement")
  val RETURN_STMT: ScExpressionElementType = new ScExpressionElementType("return statement")
  val THROW_STMT: ScExpressionElementType = new ScExpressionElementType("throw statement")
  val ASSIGN_STMT: ScExpressionElementType = new ScExpressionElementType("assign statement")
  val MATCH_STMT: ScExpressionElementType = new ScExpressionElementType("match statement")
  val TYPED_EXPR_STMT: ScExpressionElementType = new ScExpressionElementType("typed statement")

  /** ********************************************************************************** */
  /** ********************************* Expression parts ******************************* */
  /** ********************************************************************************** */

  val GENERATOR: ScalaElementType = new ScalaElementType("generator")
  val FOR_BINDING: ScalaElementType = new ScalaElementType("for binding")
  val ENUMERATORS: ScalaElementType = new ScalaElementType("enumerators")
  val GUARD: ScalaElementType = new ScalaElementType("guard")
  val ARG_EXPRS: ScalaElementType = new ScalaElementType("arguments of function")
  //Not only String, but quasiquote too
  val INTERPOLATED_PREFIX_PATTERN_REFERENCE: ScalaElementType = new ScalaElementType("Interpolated Prefix Pattern Reference")
  val NAMED_TUPLE_COMPONENT: ScalaElementType = new ScalaElementType("named tuple component")

  /** ********************************************************************************** */
  /** ************************************ PATTERNS ************************************ */
  /** ********************************************************************************** */

  val TUPLE_PATTERN: ScalaElementType = new ScalaElementType("Tuple Pattern")
  val CONSTRUCTOR_PATTERN: ScalaElementType = new ScalaElementType("Constructor Pattern")
  val PATTERN_ARGS: ScalaElementType = new ScalaElementType("Pattern arguments")
  val INFIX_PATTERN: ScalaElementType = new ScalaElementType("Infix pattern")
  val PATTERN: ScalaElementType = new ScalaElementType("Composite Pattern")
  val PATTERNS: ScalaElementType = new ScalaElementType("patterns")
  val WILDCARD_PATTERN: ScalaElementType = new ScalaElementType("any sequence")
  val CASE_CLAUSE: ScalaElementType = new ScalaElementType("case clause")
  val CASE_CLAUSES: ScalaElementType = new ScalaElementType("case clauses")
  val LITERAL_PATTERN: ScalaElementType = new ScalaElementType("literal pattern")
  val QUOTED_PATTERN: ScalaElementType = new ScalaElementType("quoted pattern")
  val INTERPOLATION_PATTERN: ScalaElementType = new ScalaElementType("interpolation pattern")
  object StableReferencePattern extends ScalaElementType("StableElementPattern")
  val PATTERN_IN_PARENTHESIS: ScalaElementType = new ScalaElementType("pattern in parenthesis")
  val GIVEN_PATTERN: ScalaElementType = new ScalaElementType("given pattern")
  val SCALA3_TYPED_PATTERN: ScalaElementType = new ScalaElementType("Scala 3 Typed Pattern")
  val NAMED_TUPLE_PATTERN: ScalaElementType = new ScalaElementType("named tuple pattern")
  val NAMED_TUPLE_PATTERN_COMPONENT: ScalaElementType = new ScalaElementType("named tuple pattern component")

  /** ************************************ TYPE PATTERNS ******************************* */

  val TYPE_PATTERN: ScalaElementType = new ScalaElementType("Type pattern")

  val REFINEMENT: ScalaElementType = new ScalaElementType("refinement")

  /** ************************************* XML ************************************ */

  val XML_EXPR: ScalaElementType = new ScalaElementType("Xml expr")
  val XML_START_TAG: ScalaElementType = new ScalaElementType("Xml start tag")
  val XML_END_TAG: ScalaElementType = new ScalaElementType("Xml end tag")
  val XML_EMPTY_TAG: ScalaElementType = new ScalaElementType("Xml empty tag")
  val XML_PI: ScalaElementType = new ScalaElementType("Xml proccessing instruction")
  val XML_CD_SECT: ScalaElementType = new ScalaElementType("Xml cdata section")
  val XML_ATTRIBUTE: ScalaElementType = new ScalaElementType("Xml attribute")
  val XML_PATTERN: ScalaElementType = new ScalaElementType("Xml pattern")
  val XML_COMMENT: ScalaElementType = new ScalaElementType("Xml comment")
  val XML_ELEMENT: ScalaElementType = new ScalaElementType("Xml element")
}
