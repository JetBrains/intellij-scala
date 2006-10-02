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
public interface ScalaElementTypes {

    IFileElementType FILE = new IFileElementType(Language.findInstance(ScalaLanguage.class));

    IElementType EXPRESSION = new ScalaElementType("expression");

//math expressions
    IElementType MATH_BINBIARY_EXPR = new ScalaElementType("mathematic binary expression");
    IElementType MATH_UNARY_EXPR = new ScalaElementType("negative unary expression");

//  bool
    IElementType BOOL_BINBIARY_EXPR = new ScalaElementType("boolean binary expression");
    IElementType BOOL_UNARY_EXPR = new ScalaElementType("negative unary expression");

// string
    IElementType STRING = new ScalaElementType("string in double quotes");

//numbers
    IElementType INTEGER = new ScalaElementType("integer number");
    IElementType FLOAT = new ScalaElementType("float number");

//regexp
    IElementType REG_EXPR = new ScalaElementType("regular expression");

//    

}
