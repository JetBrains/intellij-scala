package org.jetbrains.plugins.scala.lang.parser

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaLanguage
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 * Time: 12:53:26
 */
object ScalaElementTypes {

  /*************************************************************************************/
  /************************************** FILE *****************************************/
  /*************************************************************************************/
  val FILE = new IFileElementType(Language.findInstance(classOf[ScalaLanguage].asInstanceOf[java.lang.Class[ScalaLanguage]]))

  /*************************************************************************************/
  /************************************** OTHER ****************************************/
  /*************************************************************************************/
  val WRONGWAY = new ScalaElementType("Wrong way!")
  val TRASH = new ScalaElementType("trash")
  val EMPTY = new ScalaElementType("empty")
  val CLOSED = new ScalaElementType("closed")

  /*************************************************************************************/
  /************************************** TYPE DECLARATION *****************************/
  /*************************************************************************************/
  val PACKAGING = new ScalaElementType("packaging")
  val TMPL_DEF = new ScalaElementType("template definition")
  val TOP_TMPL_DEF = new ScalaElementType("top template definition")

  //val CASE = new ScalaElementType("case")

  val TEMPLATE_PARENTS = new ScalaElementType("template parents")
  val MIXIN_PARENTS = new ScalaElementType("mixin parents")
  val CONSTRUCTOR = new ScalaElementType("constructor")

  val TEMPLATE = new ScalaElementType("template")
  val TEMPLATE_BODY = new ScalaElementType("template body")

  //val TEMPLATE_STAT = new ScalaElementType("template statement")

  /******************* OBJECT ************************/
  val OBJECT_DEF = new ScalaElementType("object definition")
  val OBJECT_TEMPLATE = new ScalaElementType("object template")

  /******************* TRAIT ************************/
  val TRAIT_DEF = new ScalaElementType("trait definition")
  //  val TRAIT_TEMPLATE = new ScalaElementType("trait template")

  /******************* CLASS ************************/
  val CLASS_DEF = new ScalaElementType("class definition")

  val REQUIRES_BLOCK = new ScalaElementType("requires block")
  val EXTENDS_BLOCK = new ScalaElementType("extends block")
  val NEW_TEMPLATE = new ScalaElementType("new template")


  /*************************************************************************************/
  /************************* PARAMETERS OF CLASS AND FUNCTIONS *************************/
  /*************************************************************************************/

  val PARAM_TYPE = new ScalaElementType("parameter type")
  val PARAM = new ScalaElementType("parameter")
  val PARAMS = new ScalaElementType("parameters")


  val PARAM_CLAUSE = new ScalaElementType("parameters clause")
  val PARAM_CLAUSES = new ScalaElementType("parameters clause")

  /************ class ***************/
  val CLASS_PARAM = new ScalaElementType("class parameter")
  //val CLASS_PARAMS = new ScalaElementType("class parameters")
  val CLASS_PARAM_CLAUSE = new ScalaElementType("class parameter clause")
  val CLASS_PARAM_CLAUSES = new ScalaElementType("class parameter clauses")

  /************ function *************/
  val FUN_PARAM = PARAM
  //val FUN_PARAMS = new ScalaElementType("function parameters")
  //  val FUN_PARAM_CLAUSE = new ScalaElementType("function parameter clause")
  //  val FUN_PARAM_CLAUSES = new ScalaElementType("function parameter clauses")

  /*************************************************************************************/
  /************************* TYPE PARAMETERS OF CLASS AND FUNCTIONS *************************/
  /*************************************************************************************/
  val TYPE_PARAM_CLAUSE = new ScalaElementType("type parameter clause")

  /************ class ***************/
  val VARIANT_TYPE_PARAM = new ScalaElementType("variant parameter of type")

  /************ function *************/
  val TYPE_PARAM = new ScalaElementType("parameter of type")
  val TYPE_PARAMS = new ScalaElementType("parameters of type")

  /*************************************************************************************/
  /************************************** TYPES ****************************************/
  /*************************************************************************************/
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
  val SELF_TYPE = new ScalaElementType("self type")
  val EXISTENTIAL_CLAUSE = new ScalaElementType("existential clause")
  val ASCRIPTION = new ScalaElementType("ascription")
  val TUPLE_TYPE = new ScalaElementType("tuple type")
  val TYPE_IN_PARENTHESIS = new ScalaElementType("type in parenthesis")
  val PRIMARY_CONSTRUCTOR = new ScalaElementType("primary constructor")
  val EXISTENTIAL_TYPE = new ScalaElementType("existential type")
  val TYPE_PROJECTION = new ScalaElementType("type projection")
  val TYPE_GENERIC_CALL = new ScalaElementType("type generic call")
  val SEQUENCE_ARG = new ScalaElementType("sequence argument type")

  /*************************************************************************************/
  /*********************************** IDENTIFIER **************************************/
  /*************************************************************************************/

  //  val IDENTIFIER = new ScalaElementType("identifier")
  val UNIT = new ScalaElementType("unit")
  val UNIT_EXPR = new ScalaElementType("unit expression")
  val IDENTIFIER_LIST = new ScalaElementType("list of identifiers")
  val REFERENCE = new ScalaElementType("reference")

  /*************************************************************************************/
  /********************************* PACKAGE GROUP *************************************/
  /*************************************************************************************/

  val PACKAGE_STMT = new ScalaElementType("package statement")

  /*************************************************************************************/
  /********************************* IMPORT GROUP **************************************/
  /*************************************************************************************/

  val IMPORT_SELECTOR = new ScalaElementType("import selector")
  val IMPORT_SELECTOR_LIST = new ScalaElementType("import selector list")
  val IMPORT_SELECTORS = new ScalaElementType("import selectors")
  val IMPORT_EXPR = new ScalaElementType("import expression")
  val IMPORT_STMT = new ScalaElementType("import statement")
  val IMPORT = new ScalaElementType("import")
  val STABLE_ID_LIST = new ScalaElementType("stable id list")
  val UNDER = new ScalaElementType("underline")

  /*************************************************************************************/
  /********************************* METHODS, VARIABLES and ETC ************************/
  /*************************************************************************************/
  val STATEMENT_TEMPLATE = new ScalaElementType("template statement")

  /*************************************************************************************/
  /************************************ DECLARATION ************************************/
  /*************************************************************************************/
  val VALUE_DECLARATION = new ScalaElementType("value declaration")
  val VARIABLE_DECLARATION = new ScalaElementType("variable declaration")
  val FUNCTION_DECLARATION = new ScalaElementType("function declaration")
  val TYPE_DECLARATION = new ScalaElementType("type declaration")

  /*************************************************************************************/
  /************************************ DEFINITION *************************************/
  /*************************************************************************************/
  val PATTERN_DEFINITION = new ScalaElementType("pattern definition")
  val PATTERN_LIST = new ScalaElementType("List of patterns")
  val VARIABLE_DEFINITION = new ScalaElementType("variable definition")
  val TYPE_DEFINITION = new ScalaElementType("type definition")
  val EARLY_DEFINITION = new ScalaElementType("early definition")

  /**************** functions *************************/
  val FUNCTION_DEFINITION = new ScalaElementType("function definition")
  val FUN_SIG = new ScalaElementType("function signature")
  val CONSTR_EXPR = new ScalaElementType("constructor expression")
  val SELF_INVOCATION = new ScalaElementType("self invocation")

  /***************** types ******************/
  val LOWER_BOUND_TYPE = new ScalaElementType("lower bound type")
  val UPPER_BOUND_TYPE = new ScalaElementType("upper bound type")

  /*************************************************************************************/
  /******************************* MODIFIERS AND ATTRIBUTES ****************************/
  /*************************************************************************************/

  /******************* modifiers **********************/
  val MODIFIERS = new ScalaElementType("modifiers")
  val ACCESS_MODIFIER = new ScalaElementType("access modifier")

  /******************* annotation *********************/

  val NAME_VALUE_PAIR = new ScalaElementType("name value pair")
  val ANNOTATION_EXPR = new ScalaElementType("annotation expression")
  val ANNOTATION = new ScalaElementType("annotation")
  val ANNOTATIONS = new ScalaElementType("annotations")

  /*************************************************************************************/
  /************************************** LITERALS *************************************/
  /*************************************************************************************/
  val LITERAL = new ScalaElementType("Literal")
  //  String literals
  val STRING_LITERAL = new ScalaElementType("String Literal")
  // Boolean literals
  val BOOLEAN_LITERAL = new ScalaElementType("Boolean Literal")

  /*************************************************************************************/
  /************************************** EXPRESSIONS **********************************/
  /*************************************************************************************/
  /**/
  val PREFIX_EXPR = new ScalaElementType("prefix expression")
  val PREFIX = new ScalaElementType("prefix")
  val POSTFIX_EXPR = new ScalaElementType("postfix expression")
  val INFIX_EXPR = new ScalaElementType("infix expression")
  val SIMPLE_EXPR = new ScalaElementType("simple expression")

  val PARENT_EXPR = new ScalaElementType("Expression in parentheses")
  val METHOD_CALL = new ScalaElementType("Method call")
  val REFERENCE_EXPRESSION= new ScalaElementType("Reference expression")
  val THIS_REFERENCE = new ScalaElementType("This reference")
  val SUPER_REFERENCE = new ScalaElementType("Super reference")
  val PROPERTY_SELECTION = new ScalaElementType("Property selection")
  val GENERIC_CALL = new ScalaElementType("Generified call")

  //Various prefixes
  //val MINUS = new ScalaElementType("minus")

  val EXPR1 = new ScalaElementType("composite expression ")
  val FUNCTION_EXPR = new ScalaElementType("expression")
  val AN_FUN = new ScalaElementType("anonymous function")
  val BINDING = new ScalaElementType("binding")
  val GENERATOR = new ScalaElementType("generator")
  val ENUMERATOR = new ScalaElementType("enumerator")
  val ENUMERATORS = new ScalaElementType("enumerator")
  val GUARD = new ScalaElementType("guard")
  val BINDINGS = new ScalaElementType("bindings")
  val EXPRS = new ScalaElementType("list of expressions")
  val ARG_EXPRS = new ScalaElementType("arguments of function")
  val ARG_EXPRS_LIST = new ScalaElementType("list function arguments")
  val BLOCK_EXPR = new ScalaElementType("block of expressions")
  val CONSTR_BLOCK = new ScalaElementType("constructor block")
  val ERROR_STMT = new ScalaElementType("error statement")
  val BLOCK = new ScalaElementType("block")
  val TUPLE = new ScalaElementType("Tuple")
  val BLOCK_STAT = new ScalaElementType("block statements")

  //  val PARENTHESIZED_EXPR = new ScalaElementType("parenthesized expression")

  /******************************** COMPOSITE EXPRESSIONS *****************************/
  val IF_STMT = new ScalaElementType("if statement")
  val FOR_STMT = new ScalaElementType("if statement")
  val WHILE_STMT = new ScalaElementType("while statement")
  val DO_STMT = new ScalaElementType("while statement")
  val TRY_STMT = new ScalaElementType("try statement")
  val TRY_BLOCK = new ScalaElementType("try block")
  val CATCH_BLOCK = new ScalaElementType("catch block")
  val FINALLY_BLOCK = new ScalaElementType("finally block")
  val RETURN_STMT = new ScalaElementType("return statement")
  val METHOD_CLOSURE = new ScalaElementType("method closure")
  val THROW_STMT = new ScalaElementType("throw statement")
  val ASSIGN_STMT = new ScalaElementType("assign statement")
  val MATCH_STMT = new ScalaElementType("match statement")
  val TYPED_EXPR_STMT = new ScalaElementType("typed statement")


  /*************************************************************************************/
  /************************************** PATTERNS *************************************/
  /*************************************************************************************/

  val TUPLE_PATTERN = new ScalaElementType("Tuple Pattern")
  val CONSTRUCTOR_PATTERN = new ScalaElementType("Constructor Pattern")
  val INFIX_PATTERN = new ScalaElementType("Infix pattern")
  val BINDING_PATTERN = new ScalaElementType("Binding Pattern")
  val TYPED_PATTERN = new ScalaElementType("Typed Pattern")
  val PATTERN = new ScalaElementType("Composite Pattern")
  val PATTERNS = new ScalaElementType("patterns")
  val WILD_PATTERN = new ScalaElementType("any sequence")
  val CASE_CLAUSE = new ScalaElementType("case clause")
  val CASE_CLAUSES = new ScalaElementType("case clauses")
  val LITERAL_PATTERN = new ScalaElementType("literal pattern")
  val REFERENCE_PATTERN = new ScalaElementType("reference pattern")
  val XML_PATTERN = new ScalaElementType("xml pattern")
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
  val ARGUMENT_EXPR = new ScalaElementType("argument expr")

}