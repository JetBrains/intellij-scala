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
  val COMPILATION_UNIT = new ScalaElementType("compilation unit")

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

//  val CLASS_TEMPLATE = new ScalaElementType("class template")
  val TOP_DEF_TEMPLATE = new ScalaElementType("top definition template")  


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
  val PATH = new ScalaElementType("path")
  val SIMPLE_TYPE = new ScalaElementType("simple type")
  val COMPOUND_TYPE = new ScalaElementType("Compound type")
  val INFIX_TYPE = new ScalaElementType("Infix type")
  val TYPE = new ScalaElementType("common type")
  val TYPES = new ScalaElementType("common type")
  val REFINE_STAT = new ScalaElementType("refinement statement")
  val REFINEMENTS = new ScalaElementType("refinements")
  val TYPE_ARGS = new ScalaElementType("type arguments")

  /*************************************************************************************/
  /*********************************** IDENTIFIER **************************************/
  /*************************************************************************************/

//  val IDENTIFIER = new ScalaElementType("identifier")
  val UNIT = new ScalaElementType("unit")
  val IDENTIFIER_LIST = new ScalaElementType("list of identifiers")

  /*************************************************************************************/
  /********************************* PACKAGE GROUP *************************************/
  /*************************************************************************************/

  //Package
  //val PACKAGE_GROUP = new ScalaElementType("package group")
  //val PACKAGE = new ScalaElementType("package token")
  val PACKAGE_STMT = new ScalaElementType("package statement")
  val QUAL_ID = new ScalaElementType("Qualification identifier")

  /*************************************************************************************/
  /********************************* IMPORT GROUP **************************************/
  /*************************************************************************************/

  val IMPORT_SELECTOR = new ScalaElementType("import selector")
  val IMPORT_SELECTOR_LIST = new ScalaElementType("import selector list")
  val IMPORT_SELECTORS = new ScalaElementType("import selectors")
  val IMPORT_EXPR = new ScalaElementType("import expression")
  val IMPORT_EXPRS = new ScalaElementType("import expressions")
  val IMPORT_STMT = new ScalaElementType("import statement")
  val IMPORT = new ScalaElementType("import")
  val STABLE_ID_LIST = new ScalaElementType("stable id list")
  val UNDER = new ScalaElementType("underline")

  /*************************************************************************************/
  /********************************* METHODS, VARIABLES and ETC ************************/
  /*************************************************************************************/
  //val TEMPLATE_STAT_LIST = new ScalaElementType("template statements list")
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
  val VARIABLE_DEFINITION = new ScalaElementType("variable definition")
  val TYPE_DEFINITION = new ScalaElementType("type definition")

  /**************** functions *************************/
  val FUNCTION_DEFINITION = new ScalaElementType("function definition")
  val FUN_SIG = new ScalaElementType("function signature")
  val CONSTR_EXPR = new ScalaElementType("constructor expression")
  val SELF_INVOCATION = new ScalaElementType("self invocation")

  val SUPPLEMENTARY_CONSTRUCTOR = new ScalaElementType("supplementary constructor")

    /***************** types ******************/
  val LOWER_BOUND_TYPE = new ScalaElementType("lower bound type")
  val UPPER_BOUND_TYPE = new ScalaElementType("upper bound type")

  /*************************************************************************************/
  /******************************* MODIFIERS AND ATTRIBUTES ****************************/
  /*************************************************************************************/

   /****************** attributes **********************/
     val ATTRIBUTE_CLAUSE = new ScalaElementType("attribute clause")
     val ATTRIBUTE_CLAUSES = new ScalaElementType("attribute clauses")
     val ATTRIBUTE = new ScalaElementType("attribute")

   /******************* modifiers **********************/
     val MODIFIERS = new ScalaElementType("modifiers")

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
  //Various prefixes
  //val MINUS = new ScalaElementType("minus")

  val EXPR1 = new ScalaElementType("composite expression ")
  val EXPR = new ScalaElementType("expression")
  //val WRONG_EXPR = new ScalaElementType("wrong expression")
  val RESULT_EXPR = new ScalaElementType("result expression")
  val AN_FUN = new ScalaElementType("anonymous function")
  val BINDING = new ScalaElementType("binding")
  val GENERATOR = new ScalaElementType("generator")
  val ENUMERATOR = new ScalaElementType("enumerator")
  val ENUMERATORS = new ScalaElementType("enumerator")
  val BINDINGS = new ScalaElementType("bindings")
  val EXPRS = new ScalaElementType("list of expressions")
  val ARG_EXPRS = new ScalaElementType("arguments of function")
  val ARG_EXPRS_LIST = new ScalaElementType("list function arguments")
  val BLOCK_EXPR = new ScalaElementType("block of expressions")
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

  val SIMPLE_PATTERN = new ScalaElementType("simple pattern")
  val PATTERN3 = new ScalaElementType("pattern 3")
  val PATTERN2 = new ScalaElementType("pattern 2")
  val PATTERN2_LIST = new ScalaElementType("pattern 2 list")  
  val PATTERN1 = new ScalaElementType("pattern 1")
  val PATTERN = new ScalaElementType("pattern")
  val PATTERNS = new ScalaElementType("patterns")
  val WILD_PATTERN = new ScalaElementType("any sequence")
  val CASE_CLAUSE = new ScalaElementType("case clause")
  val CASE_CLAUSES = new ScalaElementType("case clauses")

  /************************************** TYPE PATTERNS ********************************/

  val ARG_TYPE_PATTERN = new ScalaElementType("Argument type pattern")
  val ARG_TYPE_PATTERNS = new ScalaElementType("Argument type patterns")
  val TYPE_PATTERN_ARGS = new ScalaElementType("Type pattern arguments")
  val SIMPLE_TYPE_PATTERN1 = new ScalaElementType("Simple type pattern 1")
  val SIMPLE_TYPE_PATTERN = new ScalaElementType("Simple type pattern")
  val TYPE_PATTERN = new ScalaElementType("Type pattern")



  val STATEMENT_SEPARATOR = new ScalaElementType("statement separator")

  val IMPLICIT_END = new ScalaElementType("implicit end")

  val COMPOSITE_TYPE = new ScalaElementType("type with =>")

  val TYPE_WITH_TYPES = new ScalaElementType("type WITH types")
  val REFINEMENT = new ScalaElementType("refinement")

  val ARGUMENT_EXPR = new ScalaElementType("argument expr")

  /*************************************************************************************/
  /***************************** PACKAGINGS & TEMPLATES ********************************/
  /*************************************************************************************/

  val PACKAGING_BIT_SET = TokenSet.create (Array.apply(PACKAGING))

  val IMPORT_STATEMENT_BIT_SET = TokenSet.create (Array.apply(IMPORT_STMT))

  val TMPL_DEF_BIT_SET = TokenSet.create (Array.apply(OBJECT_DEF, CLASS_DEF, TRAIT_DEF))

  val TMPL_OR_PACKAGING_DEF_BIT_SET = TokenSet.create (Array.apply(PACKAGING, OBJECT_DEF, CLASS_DEF, TRAIT_DEF))

  val TMPL_STMT_BIT_SET = TokenSet.create(
    Array.apply(
      PATTERN_DEFINITION,
      VARIABLE_DEFINITION,
      TYPE_DEFINITION,
      FUNCTION_DEFINITION,
      VALUE_DECLARATION,
      VARIABLE_DECLARATION,
      FUNCTION_DECLARATION,
      TYPE_DECLARATION
    )
  )

//todo: add cases
  val EXPR1_BIT_SET : TokenSet = TokenSet.create (
    Array.apply(
      IF_STMT,
      FOR_STMT,
      WHILE_STMT,
      DO_STMT,
      TRY_STMT,
      TRY_BLOCK,
      CATCH_BLOCK,
      FINALLY_BLOCK,
      RETURN_STMT,
      METHOD_CLOSURE,
      THROW_STMT,
      ASSIGN_STMT,
      MATCH_STMT,
      TYPED_EXPR_STMT,
      POSTFIX_EXPR,
      INFIX_EXPR,
      SIMPLE_EXPR,
      PREFIX_EXPR
    )
  )

  val STABLE_ID_BIT_SET = TokenSet.create(
    Array(
      STABLE_ID,
      ScalaTokenTypes.tIDENTIFIER
    )
  )

  val TYPE_BIT_SET : TokenSet = TokenSet.orSet (
    Array(
      STABLE_ID_BIT_SET,
      TokenSet.create(
        Array.apply(
          SIMPLE_TYPE,
          COMPOUND_TYPE,
          INFIX_TYPE,
          TYPE,
          TYPES,
          COMPOSITE_TYPE
        )
      )
    )
  )

  val EXPRESSION_BIT_SET = TokenSet.orSet (
    Array (
      EXPR1_BIT_SET,
      TokenSet.create (
        Array.apply(
          LITERAL,
          STRING_LITERAL,
          BOOLEAN_LITERAL,
          PREFIX_EXPR,
          PREFIX,
          POSTFIX_EXPR,
          INFIX_EXPR,
          SIMPLE_EXPR,
          EXPR1,
          EXPR,
          RESULT_EXPR,
          AN_FUN,
          BINDING,
          GENERATOR,
          ENUMERATOR,
          ENUMERATORS,
          BINDINGS,
          EXPRS,
          ARG_EXPRS,
          ARG_EXPRS_LIST,
          BLOCK_EXPR,
          ERROR_STMT,
          BLOCK,
          BLOCK_STAT
        )
      )  
    )
  )

  val SIMPLE_EXPR_BIT_SET = TokenSet.create(
    Array.apply(
      SIMPLE_EXPR,
      LITERAL,
      PATH,
      BLOCK_EXPR
    )
  )

  //todo: add clauses
  val PATTERN2_BIT_SET = TokenSet.orSet(
    Array (
      TokenSet.create(
        Array.apply(
          PATTERN2,
          PATTERN3,
          SIMPLE_PATTERN,
          LITERAL,
          STABLE_ID,
          UNIT,
          ScalaTokenTypes.tUNDER
          //([Patterns])
         //XML_PATTERN
        )
      ), STABLE_ID_BIT_SET
    )
  )
}