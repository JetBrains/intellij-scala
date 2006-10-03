package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.lang.Language;
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType;
import org.jetbrains.plugins.scala.ScalaLanguage;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 * Time: 12:53:26
 */
object ScalaElementTypes {

  val FILE = new IFileElementType(Language.findInstance(new ScalaLanguage().getClass()))

//integer, floating, boolean, character, string, symbol
  val LITERAL = new ScalaElementType("literal")

//ordinary identifier
  val STABLE_ID = new ScalaElementType("stable id")

//types
  val SIMPLE_TYPE = new ScalaElementType("simple type")
  val TYPE = new ScalaElementType("one type")
  val TYPES = new ScalaElementType("types")
  val TYPE_ARGS = new ScalaElementType("type arguments")

  val COMPOSITE_TYPE = new ScalaElementType("type with =>")

  val TYPE_WITH_TYPES = new ScalaElementType("type WITH types")
  val REFINEMENT = new ScalaElementType("refinement")

//epressions
  val SIMPLE_EXPR = new ScalaElementType("simple expression")
  val COMPOSITE_EXPR = new ScalaElementType("expression with =>")
  val EXPRESSION = new ScalaElementType("expression")
  val EXPRESSIONS_LIST = new ScalaElementType("list of expressions")

  val POSTFIX_EXPR = new ScalaElementType("postfix definition")
  val INFIX_EXPR = new ScalaElementType("infix definition")
  val PREFIX_EXPR = new ScalaElementType("prefix definition")

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
