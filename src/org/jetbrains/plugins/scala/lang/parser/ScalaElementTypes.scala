package org.jetbrains.plugins.scala.lang.parser

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.lang.Language
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.util.ClassOf

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 * Time: 12:53:26
 */
object ScalaElementTypes {

  /*************************************************************************************/
  /************************************** FILE *****************************************/
  /*************************************************************************************/
  val FILE = new IFileElementType(Language.findInstance(new ClassOf().cast[Class[ScalaLanguage]](classOf[ScalaLanguage])))

  /*************************************************************************************/
  /************************************** OTHER ****************************************/
  /*************************************************************************************/
  val WRONGWAY = new ScalaElementType("Wrong way!")
  val EMPTY = new ScalaElementType("empty")

  /*************************************************************************************/
  /************************************** CLASS ****************************************/
  /*************************************************************************************/
  val CLASS_PARAMS = new ScalaElementType("class parameters")
  val CLASS_PARAM = new ScalaElementType("class parameter")
  val CLASS_PARAM_CLAUSE = new ScalaElementType("class parameter clause")
  val CLASS_PARAM_CLAUSES = new ScalaElementType("class parameter clauses")

  val CLASS_TEMPLATE = new ScalaElementType("class template")
  val TEMPLATE_BODY = new ScalaElementType("template body")
  val TEMPLATE_PARENTS = new ScalaElementType("template parents")
  val TEMPLATE_STAT = new ScalaElementType("template statement")
  val TEMPLATE = new ScalaElementType("template")

  val TYPE_PARAM_CLAUSE = new ScalaElementType("type parameter clause")
  val PARAM = new ScalaElementType("parameter")
  val PARAM_TYPE = new ScalaElementType("parameter type")
  val VARIANT_TYPE_PARAMS = new ScalaElementType("variant parameters of type")
  val VARIANT_TYPE_PARAM = new ScalaElementType("variant parameter of type")
  val TYPE_PARAM = new ScalaElementType("parameter of type")

  /*************************************************************************************/
  /************************************** TYPES ****************************************/
  /*************************************************************************************/
  val STABLE_ID = new ScalaElementType("stable id")
  val PATH = new ScalaElementType("path")
  val SIMPLE_TYPE = new ScalaElementType("simple type")
  val TYPE1 = new ScalaElementType("type1")
  val TYPE = new ScalaElementType("common type")
  val TYPES = new ScalaElementType("types")
  val TYPE_ARGS = new ScalaElementType("type arguments")

  /*************************************************************************************/
  /*********************************** IDENTIFIER **************************************/
  /*************************************************************************************/
  val IDENTIFIER = new ScalaElementType("identifier")
  val IDENTIFIER_LIST = new ScalaElementType("list of identifiers")

  /*************************************************************************************/
  /********************************* PACKAGE GROUP *************************************/
  /*************************************************************************************/

  //Package
  val PACKAGE_GROUP = new ScalaElementType("package group")
  val PACKAGE = new ScalaElementType("package token")
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
  /************************************** TYPE DECLARATION *****************************/
  /*************************************************************************************/
  val COMPILATION_UNIT = new ScalaElementType("compilation unit")

  val PACKAGING = new ScalaElementType("packaging")

  val TMPL_DEF = new ScalaElementType("tmpl definition")

  val OBJECT = new ScalaElementType("object")
  val OBJECT_DEF = new ScalaElementType("object definition")

  val CLASS = new ScalaElementType("class")
  val CLASS_DEF = new ScalaElementType("class definition")

  val TRAIT = new ScalaElementType("trait")
  val TRAIT_STMT = new ScalaElementType("trait statement")
  val TRAIT_DEF = new ScalaElementType("trait definition")

  val CASE = new ScalaElementType("case")
  val CONSTRUCTION = new ScalaElementType("construction")

  /*************************************************************************************/
  /********************************* METHODS, VARIABLES and ETC ************************/
  /*************************************************************************************/
  val TEMPLATE_STAT_LIST = new ScalaElementType("template statements list")

  /*************************************************************************************/
  /************************************ DECLARATION ************************************/
  /*************************************************************************************/
  val DECLARATION = new ScalaElementType("declaration")

  val VALUE_DECLARATION = new ScalaElementType("value declaration")
  val VARIABLE_DECLARATION = new ScalaElementType("variable declaration")
  val FUNCTION_DECLARATION = new ScalaElementType("function declaration")
  val TYPE_DECLARATION = new ScalaElementType("type declaration")
  
  val VAL_DCL = new ScalaElementType("declaration of value")
  val VAR_DCL = new ScalaElementType("declaration of variable")
  val FUN_DCL = new ScalaElementType("declaration of function")
  val TYPE_DCL = new ScalaElementType("declaration of type")

  /*************************************************************************************/
  /************************************ DEFINITION *************************************/
  /*************************************************************************************/
  val DEFINITION = new ScalaElementType("defifnition")

  /********************************* FUNCTIONS **********************************/
  val FUNCTION_DEFINITION = new ScalaElementType("function definition")
  val FUN_DEF = new ScalaElementType("definition of function")
  val FUN_TYPE_PARAM_CLAUSE = new ScalaElementType("function type parameter clause")
  val FUN_SIG = new ScalaElementType("function signature")

  val VALUE_DEFINITION = new ScalaElementType("value definition")
  val VARIABLE_DEFINITION = new ScalaElementType("variable definition")
  val TYPE_DEFINITION = new ScalaElementType("type definition")

  val PAT_DEF = new ScalaElementType("pattern definition")
  val VAR_DEF = new ScalaElementType("definition of variable")
  val TYPE_DEF = new ScalaElementType("definition of type")

  val TYPE_PARAM_LIST = new ScalaElementType("list of type parameters")

  val PARAM_CLAUSE = new ScalaElementType("clause of parameter")
  val PARAM_LIST = new ScalaElementType("list of parameters")


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
  val PREFIX_EXPR = new ScalaElementType("prefix expression")
  val PREFIX = new ScalaElementType("Simple prefix of prefix expression")
  val POSTFIX_EXPR = new ScalaElementType("postfix expression")
  val INFIX_EXPR = new ScalaElementType("infix expression")
  val SIMPLE_EXPR = new ScalaElementType("simple expression")
  //Various prefixes
  //val MINUS = new ScalaElementType("minus")

  val EXPR1 = new ScalaElementType("compositte expression ")
  val EXPR = new ScalaElementType("expression")
  val EXPRS = new ScalaElementType("list of expressions")

  val ARG_EXPRS = new ScalaElementType("arguments of function")
  val BLOCK_EXPR = new ScalaElementType("block of expressions")
  val BLOCK = new ScalaElementType("block")
  val BLOCK_STAT = new ScalaElementType("block statements")


  /*************************************************************************************/
  /************************************** KEYWORDS *************************************/
  /*************************************************************************************/
  /*val THIS = new ScalaElementType("this")
  val WITH = new ScalaElementType("with")
  val SUPER = new ScalaElementType("super")
  // Primitives
  val DOT = new ScalaElementType("DOT")
  val INNER_CLASS = new ScalaElementType("#")
  val COMMA = new ScalaElementType("COMMA")
  val SEMICOLON = new ScalaElementType("SEMICOLON")
  val LSQBRACKET = new ScalaElementType("LSQBRACKET")
  val LPARENTHIS = new ScalaElementType("LPARENTHIS")
  val RPARENTHIS = new ScalaElementType("RPARENTHIS")
  val RSQBRACKET = new ScalaElementType("RSQBRACKET") */
  val STATEMENT_SEPARATOR = new ScalaElementType("statement separator")

//ordinary identifier

//Attributes
  val ATTRIBUTE_CLAUSE = new ScalaElementType("attribute clause")
  val ATTRIBUTE = new ScalaElementType("attribute")

//Modifiers
  val MODIFIER = new ScalaElementType("modifier")
  val LOCAL_MODIFIER = new ScalaElementType("local modifier")
  val ACCESS_MODIFIER = new ScalaElementType("access control modifier : public and private")
  val OVERRIDE = new ScalaElementType("override")

//top
  val TOP_STAT = new ScalaElementType("top stat")
  val TOP_STAT_SEQ = new ScalaElementType("top stat sequence")

//types

  val IMPLICIT_END = new ScalaElementType("implicit end")


  val COMPOSITE_TYPE = new ScalaElementType("type with =>")

  val TYPE_WITH_TYPES = new ScalaElementType("type WITH types")
  val REFINEMENT = new ScalaElementType("refinement")

//other
  val IF_STMT = new ScalaElementType("only if statement")

  val TRY_STMT = new ScalaElementType("try statament")

  val WHILE_STMT = new ScalaElementType("while statement")
  val DO_WHILE_STMT = new ScalaElementType("do-while construction")

  val FOR_STMT = new ScalaElementType("for statament")

  val THROW = new ScalaElementType("throw")

  val RETURN = new ScalaElementType("return")

  val ASSIGNMENT = new ScalaElementType("assignment")

  val MATCH = new ScalaElementType("match construction")

  //method closure - ?

  val ARGUMENT_EXPR = new ScalaElementType("argument expr")

  val CASE_CLAUSES = new ScalaElementType("argument expr")





  //todo: supplement elements
}