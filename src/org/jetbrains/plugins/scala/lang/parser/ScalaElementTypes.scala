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
public abstract ScalaElementTypes {

    IFileElementType FILE = new IFileElementType(Language.findInstance(ScalaLanguage.getClass()));

    IElementType EXPRESSION = new ScalaElementType("expression");

//math expressions
    IElementType MATH_BINBIARY_EXPR = new ScalaElementType("mathematic binary expression");
    IElementType MATH_UNARY_EXPR = new ScalaElementType("negative unary expression");

//  bool
    IElementType BOOL_BINBIARY_EXPR = new ScalaElementType("boolean binary expression");
    IElementType BOOL_UNARY_EXPR = new ScalaElementType("negative unary expression");

//assignment
    IElementType ASSIGNMENT_EXPR = new ScalaElementType("assignment expression");

// string
    IElementType STRING = new ScalaElementType("string in double quotes");
    IElementType SYMBOL = new ScalaElementType("symbol in quotes");

//numbers
    IElementType INTEGER = new ScalaElementType("integer number");
    IElementType FLOAT = new ScalaElementType("float number");

//regexp
    IElementType REG_EXPR = new ScalaElementType("regular expression");

//classes and objects

    IElementType CLASS = new ScalaElementType("class");
    IElementType CLASS_PARAM = new ScalaElementType("paramertrize class");

    IElementType OBJECT = new ScalaElementType("object");

//method
    IElementType METHOD = new ScalaElementType("method");

//case
   IElementType CASE_ClASS = new ScalaElementType("case in subclass definition");
   IElementType CASE_MATCH = new ScalaElementType("case in matching");

//variables
   IElementType VAR = new ScalaElementType("changable variable");
   IElementType VAL = new ScalaElementType("unchangable variable");

   IElementType IDENTIFIER = new ScalaElementType("identifier");


//if else stmts
   IElementType IF_STMT = new ScalaElementType("if statement");
   IElementType IF_ELSE = new ScalaElementType("esle statement");

}
