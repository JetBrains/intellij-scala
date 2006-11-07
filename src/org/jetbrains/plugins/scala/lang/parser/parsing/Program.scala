package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.util._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
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

         // Expression functionality testing
         if (PostfixExpr.POSTFIX_FIRST.contains(builder.getTokenType)) {
           PostfixExpr parse (builder)
         } else builder advanceLexer

         // Types functionality testing
         /*
         if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType) ||
              ScalaTokenTypes.kTHIS.equals(builder.getTokenType) ||
              ScalaTokenTypes.kSUPER.equals(builder.getTokenType) ||
              ScalaTokenTypes.tLPARENTHIS.equals(builder.getTokenType)) {
           var res = Type parse (builder)
           if (res.equals(ScalaElementTypes.WRONGWAY)) builder advanceLexer
         } else builder advanceLexer
         */

      }
    }


    var marker = builder.mark()
    rollForward
    ParserUtils.rollForward(builder)
    if ( !builder.eof() ){
      //new Top parse(builder) //handle top level - package, import
      //Console.println("CompilationUnit invoke ")
      CompilationUnit.parse(builder)
      //Console.println("CompilationUnit invoked ")
    }

    /*while ( !builder.eof() ){
      builder.advanceLexer
    } */


    parseNext
    ParserUtils.rollForward(builder)
    marker.done(ScalaElementTypes.FILE)
  }

}