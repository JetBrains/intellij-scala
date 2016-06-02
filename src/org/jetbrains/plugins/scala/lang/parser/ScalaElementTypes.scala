package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.{ASTNode, Language}
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.{ICompositeElementType, IElementType, IErrorCounterReparseableElementType, IStubFileElementType}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.lexer.{ScalaElementType, ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockExprImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures.{ScClassParameterElementType, ScParamClauseElementType, ScParamClausesElementType, ScParameterElementType}

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 *
 */
trait ElementTypes {
  val file: IStubFileElementType[_ <: PsiFileStub[_ <: PsiFile]]

  val classDefinition: ScClassDefinitionElementType
  val objectDefinition: ScObjectDefinitionElementType
  val traitDefinition: ScTraitDefinitionElementType
}

object ScalaElementTypes extends ElementTypes {
  val DUMMY_ELEMENT = new ScalaElementType("Dummy Elemnet")


  //Stub element types
  val file = new ScStubFileElementType

  val classDefinition = new ScClassDefinitionElementType
  val objectDefinition = new ScObjectDefinitionElementType
  val traitDefinition = new ScTraitDefinitionElementType

  val PACKAGING = new ScPackagingElementType
  val EXTENDS_BLOCK = new ScExtendsBlockElementType

  val CLASS_PARENTS = new ScClassParentsElementType
  val TRAIT_PARENTS = new ScTraitParentsElementType
  val CONSTRUCTOR = new ScalaElementType("constructor", true)

  val TEMPLATE = new ScalaElementType("template", true)
  val TEMPLATE_BODY = new ScTemplateBodyElementType


  val REQUIRES_BLOCK = new ScalaElementType("requires block")
  val NEW_TEMPLATE = new ScNewTemplateDefinitionStubElementType


  /** ***********************************************************************************/
  /************************* PARAMETERS OF CLASS AND FUNCTIONS *************************/
  /** ***********************************************************************************/

  val PARAM_TYPE = new ScalaElementType("parameter type")
  val PARAM = new ScParameterElementType
  val PARAM_CLAUSE = new ScParamClauseElementType
  val PARAM_CLAUSES = new ScParamClausesElementType

  /************ class ***************/
  val CLASS_PARAM = new ScClassParameterElementType

  /** ***********************************************************************************/
  /************************* TYPE PARAMETERS OF CLASS AND FUNCTIONS *************************/
  /** ***********************************************************************************/
  val TYPE_PARAM_CLAUSE = new ScTypeParamClauseElementType

  /************ class ***************/
  val VARIANT_TYPE_PARAM = new ScalaElementType("variant parameter of type")

  /************ function *************/
  val TYPE_PARAM = new ScTypeParamElementType
  val TYPE_PARAMS = new ScalaElementType("parameters of type")

  /** ***********************************************************************************/
  /************************************** TYPES ****************************************/
  /** ***********************************************************************************/
  val STABLE_ID = new ScalaElementType("stable id")
  val STABLE_ID_ID = new ScalaElementType("stable id")
  val SIMPLE_TYPE = new ScalaElementType("simple type")
  val COMPOUND_TYPE = new ScalaElementType("Compound type")
  val INFIX_TYPE = new ScalaElementType("Infix type")
  val TYPE = new ScalaElementType("common type")
  val TYPES = new ScalaElementType("common type")
  val REFINE_STAT = new ScalaElementType("refinement statement")
  val REFINEMENTS = new ScalaElementType("refinements")
  val TYPE_ARGS = new ScalaElementType("type arguments")
  val ANNOT_TYPE = new ScalaElementType("annotation type")
  val SELF_TYPE = new ScSelfTypeElementElementType
  val EXISTENTIAL_CLAUSE = new ScalaElementType("existential clause")
  val WILDCARD_TYPE = new ScalaElementType("wildcard type")
  val ASCRIPTION = new ScalaElementType("ascription")
  val TUPLE_TYPE = new ScalaElementType("tuple type")
  val TYPE_IN_PARENTHESIS = new ScalaElementType("type in parenthesis")
  val PRIMARY_CONSTRUCTOR = new ScPrimaryConstructorElementType
  val EXISTENTIAL_TYPE = new ScalaElementType("existential type")
  val TYPE_PROJECTION = new ScalaElementType("type projection")
  val TYPE_GENERIC_CALL = new ScalaElementType("type generic call")
  val SEQUENCE_ARG = new ScalaElementType("sequence argument type")
  val TYPE_VARIABLE = new ScalaElementType("type variable")

  /** ***********************************************************************************/
  /*********************************** IDENTIFIER **************************************/
  /** ***********************************************************************************/

  val UNIT_EXPR = new ScalaElementType("unit expression")
  val IDENTIFIER_LIST = new ScIdListElementType
  val FIELD_ID = new ScFieldIdElementType
  val REFERENCE = new ScalaElementType("reference")

  /** ***********************************************************************************/
  /********************************* IMPORT GROUP **************************************/
  /** ***********************************************************************************/

  val IMPORT_SELECTOR = new ScImportSelectorElementType
  val IMPORT_SELECTORS = new ScImportSelectorsElementType
  val IMPORT_EXPR = new ScImportExprElementType
  val IMPORT_STMT = new ScImportStmtElementType
  val IMPORT = new ScalaElementType("import")
  val STABLE_ID_LIST = new ScalaElementType("stable id list")

  /** ***********************************************************************************/
  /********************************* METHODS, VARIABLES and ETC ************************/
  /** ***********************************************************************************/
  val STATEMENT_TEMPLATE = new ScalaElementType("template statement")

  /** ***********************************************************************************/
  /************************************ DECLARATION ************************************/
  /** ***********************************************************************************/
  val VALUE_DECLARATION : ScValueElementType[_ <: ScValue] = new ScValueDeclarationElementType
  val VARIABLE_DECLARATION : ScVariableElementType[_ <: ScVariable] = new ScVariableDeclarationElementType
  val FUNCTION_DECLARATION :ScFunctionElementType[_ <: ScFunction] = new ScFunctionDeclarationElementType
  val TYPE_DECLARATION = new ScTypeAliasDeclarationElementType

  /** ***********************************************************************************/
  /************************************ DEFINITION *************************************/
  /** ***********************************************************************************/
  val PATTERN_DEFINITION : ScValueElementType[_ <: ScValue] = new ScValueDefinitionElementType
  val PATTERN_LIST = new ScPatternListElementType
  val VARIABLE_DEFINITION : ScVariableElementType[_ <: ScVariable] = new ScVariableDefinitionElementType
  val TYPE_DEFINITION = new ScTypeAliasDefinitionElementType
  val EARLY_DEFINITIONS = new ScEarlyDefinitionsElementType

  /**************** functions *************************/
  val FUNCTION_DEFINITION = new ScFunctionDefinitionElementType
  val MACRO_DEFINITION = new ScMacroDefinitionElementType
  val FUN_SIG = new ScalaElementType("function signature")
  val CONSTR_EXPR = new ScalaElementType("constructor expression")
  val SELF_INVOCATION = new ScalaElementType("self invocation")

  /***************** types ******************/
  val LOWER_BOUND_TYPE = new ScalaElementType("lower bound type")
  val UPPER_BOUND_TYPE = new ScalaElementType("upper bound type")

  /** ***********************************************************************************/
  /******************************* MODIFIERS AND ATTRIBUTES ****************************/
  /** ***********************************************************************************/

  /******************* modifiers **********************/
  val MODIFIERS = new ScModifiersElementType("moifiers")
  val ACCESS_MODIFIER = new ScAccessModifierElementType

  /******************* annotation *********************/

  val NAME_VALUE_PAIR = new ScalaElementType("name value pair")
  val ANNOTATION_EXPR = new ScalaElementType("annotation expression")
  val ANNOTATION = new ScAnnotationElementType
  val ANNOTATIONS = new ScAnnotationsElementType

  /** ***********************************************************************************/
  /************************************** LITERALS *************************************/
  /** ***********************************************************************************/
  val LITERAL = new ScalaElementType("Literal")
  //  String literals
  val STRING_LITERAL = new ScalaElementType("String Literal")
  val INTERPOLATED_STRING_LITERAL = new ScalaElementType("Interpolated String Literal")
  //Not only String, but quasiquote too
  val INTERPOLATED_PREFIX_PATTERN_REFERENCE = new ScalaElementType("Interpolated Prefix Pattern Reference")
  val INTERPOLATED_PREFIX_LITERAL_REFERENCE = new ScalaElementType("Interpolated Prefix Literal Reference")
  // Boolean literals
  val BOOLEAN_LITERAL = new ScalaElementType("Boolean Literal")

  /** ***********************************************************************************/
  /************************************** EXPRESSIONS **********************************/
  /** ***********************************************************************************/
  /**/
  val PREFIX_EXPR = new ScalaElementType("prefix expression")
  val PREFIX = new ScalaElementType("prefix")
  val POSTFIX_EXPR = new ScalaElementType("postfix expression")
  val INFIX_EXPR = new ScalaElementType("infix expression")
  val PLACEHOLDER_EXPR = new ScalaElementType("simple expression")

  val PARENT_EXPR = new ScalaElementType("Expression in parentheses")
  val METHOD_CALL = new ScalaElementType("Method call")
  val REFERENCE_EXPRESSION = new ScalaElementType("Reference expression")
  val THIS_REFERENCE = new ScalaElementType("This reference")
  val SUPER_REFERENCE = new ScalaElementType("Super reference")
  val GENERIC_CALL = new ScalaElementType("Generified call")

  val EXPR1 = new ScalaElementType("composite expression ")
  val FUNCTION_EXPR = new ScalaElementType("expression")
  val AN_FUN = new ScalaElementType("anonymous function")
  val GENERATOR = new ScalaElementType("generator")
  val ENUMERATOR = new ScalaElementType("enumerator")
  val ENUMERATORS = new ScalaElementType("enumerator")
  val GUARD = new ScalaElementType("guard")
  val EXPRS = new ScalaElementType("list of expressions")
  val ARG_EXPRS = new ScalaElementType("arguments of function")
  val BLOCK_EXPR = new ScCodeBlockElementType
  val CONSTR_BLOCK = new ScalaElementType("constructor block")
  val ERROR_STMT = new ScalaElementType("error statement")
  val BLOCK = new ScalaElementType("block")
  val TUPLE = new ScalaElementType("Tuple")

  /******************************** COMPOSITE EXPRESSIONS *****************************/
  val IF_STMT = new ScalaElementType("if statement")
  val FOR_STMT = new ScalaElementType("for statement")
  val WHILE_STMT = new ScalaElementType("while statement")
  val DO_STMT = new ScalaElementType("do-while statement")
  val TRY_STMT = new ScalaElementType("try statement")
  val TRY_BLOCK = new ScalaElementType("try block")
  val CATCH_BLOCK = new ScalaElementType("catch block")
  val FINALLY_BLOCK = new ScalaElementType("finally block")
  val RETURN_STMT = new ScalaElementType("return statement")
  val THROW_STMT = new ScalaElementType("throw statement")
  val ASSIGN_STMT = new ScalaElementType("assign statement")
  val MATCH_STMT = new ScalaElementType("match statement")
  val TYPED_EXPR_STMT = new ScalaElementType("typed statement")


  /** ***********************************************************************************/
  /************************************** PATTERNS *************************************/
  /** ***********************************************************************************/

  val TUPLE_PATTERN = new ScalaElementType("Tuple Pattern")
  val SEQ_WILDCARD = new ScalaElementType("Sequence Wildcard")
  val CONSTRUCTOR_PATTERN = new ScalaElementType("Constructor Pattern")
  val PATTERN_ARGS = new ScalaElementType("Pattern arguments")
  val INFIX_PATTERN = new ScalaElementType("Infix pattern")
  val NAMING_PATTERN = new ScalaElementType("Binding Pattern")
  val TYPED_PATTERN = new ScalaElementType("Typed Pattern")
  val PATTERN = new ScalaElementType("Composite Pattern")
  val PATTERNS = new ScalaElementType("patterns")
  val WILDCARD_PATTERN = new ScalaElementType("any sequence")
  val CASE_CLAUSE = new ScalaElementType("case clause")
  val CASE_CLAUSES = new ScalaElementType("case clauses")
  val LITERAL_PATTERN = new ScalaElementType("literal pattern")
  val INTERPOLATION_PATTERN = new ScalaElementType("interpolation pattern")
  val REFERENCE_PATTERN = new ScReferencePatternElementType
  val STABLE_REFERENCE_PATTERN = new ScalaElementType("stable reference pattern")
  val PATTERN_IN_PARENTHESIS = new ScalaElementType("pattern in parenthesis")

  /************************************** TYPE PATTERNS ********************************/

  val ARG_TYPE_PATTERN = new ScalaElementType("Argument type pattern")
  val ARG_TYPE_PATTERNS = new ScalaElementType("Argument type patterns")
  val TYPE_PATTERN_ARGS = new ScalaElementType("Type pattern arguments")
  val TYPE_PATTERN = new ScalaElementType("Type pattern")



  val STATEMENT_SEPARATOR = new ScalaElementType("statement separator")
  val IMPLICIT_END = new ScalaElementType("implicit end")
  val COMPOSITE_TYPE = new ScalaElementType("type with =>")
  val TYPE_WITH_TYPES = new ScalaElementType("type WITH types")
  val REFINEMENT = new ScalaElementType("refinement")

  /*************************************** XML *************************************/

  val XML_EXPR = new ScalaElementType("Xml expr")
  val XML_START_TAG = new ScalaElementType("Xml start tag")
  val XML_END_TAG = new ScalaElementType("Xml end tag")
  val XML_EMPTY_TAG = new ScalaElementType("Xml empty tag")
  val XML_PI = new ScalaElementType("Xml proccessing instruction")
  val XML_CD_SECT = new ScalaElementType("Xml cdata section")
  val XML_ATTRIBUTE = new ScalaElementType("Xml attribute")
  val XML_PATTERN = new ScalaElementType("Xml pattern")
  val XML_COMMENT = new ScalaElementType("Xml comment")
  val XML_ELEMENT = new ScalaElementType("Xml element")

  class ScCodeBlockElementType() extends IErrorCounterReparseableElementType("block of expressions",
    ScalaFileType.SCALA_LANGUAGE) with ICompositeElementType {

    override def createNode(text: CharSequence): ASTNode = {
      new ScBlockExprImpl(text)
    }

    @NotNull def createCompositeNode: ASTNode = {
      new ScBlockExprImpl(null)
    }

    def getErrorsCount(seq: CharSequence, fileLanguage: Language, project: Project): Int = {
      import com.intellij.psi.tree.IErrorCounterReparseableElementType._
      val lexer: Lexer = new ScalaLexer
      lexer.start(seq)
      if (lexer.getTokenType != ScalaTokenTypes.tLBRACE) return FATAL_ERROR
      lexer.advance()
      var balance: Int = 1
      var flag = false
      while (!flag) {
        val tp : IElementType = lexer.getTokenType
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