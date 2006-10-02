package org.jetbrains.plugins.scala.parser;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 * Time: 12:53:26
 */
public interface ScalaElementTypes {
    
    IElementType EXPRESSION = new ScalaElementType("expression");

//math expressions
    IElementType MATH_BINBIARY_EXPR = new ScalaElementType("mathematic binary expression");
    IElementType MATH_UNARY_EXPR = new ScalaElementType("negative unary expression");

//  bool
    IElementType BOOL_BINBIARY_EXPR = new ScalaElementType("boolean binary expression");
    IElementType BOOL_UNARY_EXPR = new ScalaElementType("negative unary expression");

//


}
