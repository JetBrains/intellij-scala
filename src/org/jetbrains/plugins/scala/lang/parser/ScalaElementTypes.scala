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
abstract class ScalaElementTypes {

    //val FILE = new IFileElementType(Language.findInstance(ScalaLanguage.class));

    val EXPRESSION = new ScalaElementType("expression");

//math expressions
    val MATH_BINBIARY_EXPR = new ScalaElementType("mathematic binary expression");
    val MATH_UNARY_EXPR = new ScalaElementType("negative unary expression");

//  bool
    val BOOL_BINBIARY_EXPR = new ScalaElementType("boolean binary expression");
    val BOOL_UNARY_EXPR = new ScalaElementType("negative unary expression");

//assignment
    val ASSIGNMENT_EXPR = new ScalaElementType("assignment expression");

// string
    val STRING = new ScalaElementType("string in double quotes");
    val SYMBOL = new ScalaElementType("symbol in quotes");

//numbers
    val INTEGER = new ScalaElementType("integer number");
    val FLOAT = new ScalaElementType("float number");

//regexp
    val REG_EXPR = new ScalaElementType("regular expression");

//classes and objects

    val CLASS = new ScalaElementType("class");
    val CLASS_PARAM = new ScalaElementType("paramertrize class");

    val OBJECT = new ScalaElementType("object");

//method
    val METHOD = new ScalaElementType("method");

//case
   val CASE_ClASS = new ScalaElementType("case in subclass definition");
   val CASE_MATCH = new ScalaElementType("case in matching");

//variables
   val VAR = new ScalaElementType("changable variable");
   val VAL = new ScalaElementType("unchangable variable");

   val IDENTIFIER = new ScalaElementType("identifier");

//if else stmts
   val IF_STMT = new ScalaElementType("if statement");
   val IF_ELSE = new ScalaElementType("else statement")

}
