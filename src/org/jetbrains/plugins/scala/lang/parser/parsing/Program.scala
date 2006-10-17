package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Top
import org.jetbrains.plugins.scala.lang.parser.util._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expression
import org.jetbrains.plugins.scala.lang.parser.parsing.types._


import com.intellij.lang.PsiBuilder

class Program extends ScalaTokenTypes {
  def parse(builder: PsiBuilder): Unit = {

    var flag = true

    def rollForward : Unit = {
      while ( !builder.eof() && flag){
         builder.getTokenType match{
           case ScalaTokenTypes.tLINE_TERMINATOR
           | ScalaTokenTypes.tSEMICOLON => builder.advanceLexer
           case _ => flag = false
         }
      }
      flag = true
    }

    def parseNext : Unit = {
      while ( !builder.eof() ) {
         rollForward

         if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType) ||
              ScalaTokenTypes.kTHIS.equals(builder.getTokenType) ||
              ScalaTokenTypes.kSUPER.equals(builder.getTokenType)) {
           StableId parse (builder)
         } else

         if (Expression.POSTFIX_FIRST.contains(builder.getTokenType)) {
           Expression parsePostfixExpr (builder)
         } else builder advanceLexer
      }
    }

    
    var marker = builder.mark()
    rollForward

    if ( !builder.eof() ){
      //new Top parse(builder) //handle top level - package, import
      Console.println("CompilationUnit invoke ")
      CompilationUnit.parse(builder)
      Console.println("CompilationUnit invoked ")
    }

    /*while ( !builder.eof() ){
      builder.advanceLexer
    } */


    parseNext
    marker.done(ScalaElementTypes.FILE)


  }

}