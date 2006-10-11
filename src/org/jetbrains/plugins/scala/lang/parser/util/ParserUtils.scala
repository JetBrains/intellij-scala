package org.jetbrains.plugins.scala.lang.parser.util

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Top
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Literal
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.PrefixExpression
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.SimpleExpression

import com.intellij.lang.PsiBuilder

object ParserUtils {

  def rollForward(builder: PsiBuilder) : Unit = {
    var flag = true
    while ( !builder.eof() && flag){
       builder.getTokenType match{
         case ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE => builder.advanceLexer
         case _ => flag = false
       }
    }
  }

}