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
  val PARAM = new ScalaElementType("parameter")
  val PARAM_TYPE = new ScalaElementType("parameter type")

  /*************************************************************************************/
  /************************************** TYPES ****************************************/
  /*************************************************************************************/
  val STABLE_ID = new ScalaElementType("stable id")
  val PATH = new ScalaElementType("path")
  val SIMPLE_TYPE = new ScalaElementType("simple type")
  val TYPE1 = new ScalaElementType("type1")
  val TYPE = new ScalaElementType("common type")
  val KEY_TYPE = new ScalaElementType("type keyword")
  val TYPEARGS = new ScalaElementType("type arguments")

  /*************************************************************************************/
  /********************************* PACKAGE GROUP *************************************/
  /*************************************************************************************/

  //Package
  val PACKAGE_GROUP = new ScalaElementType("package group")
  val PACKAGE_STMT = new ScalaElementType("package statement")
  val QUAL_ID = new ScalaElementType("Qualification identifier")
        
  /*************************************************************************************/
  /********************************* IMPORT GROUP **************************************/
  /*************************************************************************************/

  val IMPORT_SELECTORS = new ScalaElementType("import selectors")
  val IMPORT_SELECTOR = new ScalaElementType("import selector")
  val IMPORT_EXPR = new ScalaElementType("import selector")
  val IMPORT_STMT = new ScalaElementType("import statement")
  val IMPORT_LIST = new ScalaElementType("import list")
  val STABLE_ID_LIST = new ScalaElementType("stable id list")

  /*************************************************************************************/
  /************************************** TYPE DECLARATION *****************************/
  /*************************************************************************************/
  val TMPL_DEF = new ScalaElementType("tmpl definition")

  val OBJECT_STMT = new ScalaElementType("object statement")
  val OBJECT_DEF = new ScalaElementType("object definition")

  val CLASS_STMT = new ScalaElementType("class statement")
  val CLASS_DEF = new ScalaElementType("class definition")

  val TRAIT_STMT = new ScalaElementType("trait statement")
  val TRAIT_DEF = new ScalaElementType("trait definition")

  val TYPE_PARAM_CLAUSE = new ScalaElementType("type parameters clause")
  val CLASS_PARAM_CLAUSES = new ScalaElementType("class parameters clauses")
  val CLASS_PARAM_CLAUSE = new ScalaElementType("class parameters clause")

  /*************************************************************************************/
  /************************************** LITERALS *************************************/
  /*************************************************************************************/
  val LITERAL = new ScalaElementType("Literal")
  //  String literals
  val STRING_LITERAL = new ScalaElementType("String Literal")
  val STRING_CONTENT = new ScalaElementType("String content")
  val STRING_BEGIN = new ScalaElementType("String begin")
  val STRING_END = new ScalaElementType("String end")
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

  val COMPOSITE_EXPR = new ScalaElementType("expression with =>")
  val EXPRESSION = new ScalaElementType("expression")
  val EXPRESSIONS_LIST = new ScalaElementType("list of expressions")

  /*************************************************************************************/
  /************************************** KEYWORDS *************************************/
  /*************************************************************************************/
  // Primitives
  val INNER_CLASS = new ScalaElementType("#")
  val STATEMENT_SEPARATOR = new ScalaElementType("statement separator")

//ordinary identifier

//Attributes
  val ATTRIBUTE_CLAUSE = new ScalaElementType("attribute clause")
  val ATTRIBUTE = new ScalaElementType("attribute")

//Modifiers
  val MODIFIER = new ScalaElementType("modifier")
  val LOCAL_MODIFIER = new ScalaElementType("local modifier")
  val MODIFIER_ACCESS = new ScalaElementType("access control modifier : public and private")

//top
  val TOP_STAT = new ScalaElementType("top stat")
  val TOP_STAT_SEQ = new ScalaElementType("top stat sequence")

//types
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