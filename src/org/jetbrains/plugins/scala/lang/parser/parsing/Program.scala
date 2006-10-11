package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Top
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Literal
import org.jetbrains.plugins.scala.lang.parser.util._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixExpression
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.PrefixExpression
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.SimpleExpression

import com.intellij.lang.PsiBuilder

class Program extends ScalaTokenTypes {
  def parse(builder: PsiBuilder): Unit = {

    var flag = true  

    def rollForward : Unit = {
      while ( !builder.eof() && flag){
         builder.getTokenType match{
           case ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE
           | ScalaTokenTypes.tSEMICOLON => builder.advanceLexer
           case _ => flag = false
         }
      }
      flag = true
    }

    def parseNext : Unit = {
      while ( !builder.eof() ) {
         rollForward
         if (PrefixExpression.FIRST.contains(builder.getTokenType)) {
           PrefixExpression parse (builder)
         } else builder advanceLexer
      }
    }

    var marker = builder.mark()
    rollForward

    if ( !builder.eof() ){
      new Top parse(builder) //handle top level - package, import
    }

    parseNext
    marker.done(ScalaElementTypes.FILE)


  }

}