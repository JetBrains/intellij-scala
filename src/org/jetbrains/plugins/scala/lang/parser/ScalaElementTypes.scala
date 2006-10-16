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
  val FILE = new IFileElementType(Language.findInstance(new ClassOf().cast[Class[ScalaLanguage]]( classOf[ScalaLanguage] )))

  /*************************************************************************************/
  /*********************************** IDENTIFIER **************************************/
  /*************************************************************************************/
  val IDENTIFIER = new ScalaElementType("identifier")

  /*************************************************************************************/
  /********************************* PACKAGE GROUP *************************************/
  /*************************************************************************************/

  //Package
  val PACKAGE_GROUP = new ScalaElementType("package group")
  val PACKAGE = new ScalaElementType("package token")
  val QUALID = new ScalaElementType("Qualification identifier")

  /*************************************************************************************/
  /********************************* IMPORT GROUP *************************************/
  /*************************************************************************************/

  val IMPORT_GROUP = new ScalaElementType("import group")
  val IMPORT_LIST = new ScalaElementType("import list")
  val IMPORT_STMT = new ScalaElementType("import statement")
  val IMPORT = new ScalaElementType("import")
  val STABLE_ID = new ScalaElementType("stable id")
  val STABLE_ID_LIST = new ScalaElementType("stable id list")
  val UNDER = new ScalaElementType("underline")

  /*************************************************************************************/
  /************************************** TYPE DECLARATION *************************************/
  /*************************************************************************************/
  val TMPL_DEF = new ScalaElementType("tmpl definition")

  val OBJECT = new ScalaElementType("object")
  val OBJECT_STMT = new ScalaElementType("object statement")
  val OBJECT_DEF = new ScalaElementType("object definition")

  val CLASS = new ScalaElementType("class")
  val CLASS_STMT = new ScalaElementType("class statement")
  val CLASS_DEF = new ScalaElementType("class definition")

  val TRAIT = new ScalaElementType("trait")
  val TRAIT_STMT = new ScalaElementType("trait statement")
  val TRAIT_DEF = new ScalaElementType("trait definition")

  val CASE = new ScalaElementType("case")

  /*************************************************************************************/
  /************************************** LITERALS *************************************/
  /*************************************************************************************/
  val LITERAL = new ScalaElementType("Literal")
  val INTEGER_LITERAL = new ScalaElementType("Integer Literal")
  val FLOATING_POINT_LITERAL = new ScalaElementType("Floating Point Literal")
  val CHARACTER_LITERAL = new ScalaElementType("Character Literal")
  //  String literals
  val STRING_LITERAL = new ScalaElementType("String Literal")
  val STRING_CONTENT = new ScalaElementType("String content")
  val STRING_BEGIN = new ScalaElementType("String begin")
  val STRING_END = new ScalaElementType("String end")
  // Boolean literals
  val BOOLEAN_LITERAL = new ScalaElementType("Boolean Literal")
  val TRUE = new ScalaElementType("true")
  val FALSE = new ScalaElementType("false")
  //null
  val NULL = new ScalaElementType("null")

  /*************************************************************************************/
  /************************************** EXPRESSIONS **********************************/
  /*************************************************************************************/
  val PREFIX_EXPR = new ScalaElementType("prefix expression")
  val PREFIX = new ScalaElementType("Simple prefix of prefix expression")
  val POSTFIX_EXPR = new ScalaElementType("postfix expression")
  val INFIX_EXPR = new ScalaElementType("infix expression")
  val SIMPLE_EXPR = new ScalaElementType("simple expression")
  //Various prefixes
  val MINUS = new ScalaElementType("minus")

  val COMPOSITE_EXPR = new ScalaElementType("expression with =>")
  val EXPRESSION = new ScalaElementType("expression")
  val EXPRESSIONS_LIST = new ScalaElementType("list of expressions")

  /*************************************************************************************/
  /************************************** KEYWORDS *************************************/
  /*************************************************************************************/
  val THIS = new ScalaElementType("this")
  val SUPER = new ScalaElementType("super")
  // Primitives
  val DOT = new ScalaElementType("DOT")
  val COMMA = new ScalaElementType("COMMA")
  val SEMICOLON = new ScalaElementType("SEMICOLON")
  val LSQBRACKET = new ScalaElementType("LSQBRACKET")
  val RSQBRACKET = new ScalaElementType("RSQBRACKET")

//ordinary identifier

//type declaration

//types
  val SIMPLE_TYPE = new ScalaElementType("simple type")
  val TYPE = new ScalaElementType("one type")
  val TYPES = new ScalaElementType("types")
  val TYPE_ARGS = new ScalaElementType("type arguments")

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

  val BLOCK = new ScalaElementType("block")




  //todo: supplement elements
}
