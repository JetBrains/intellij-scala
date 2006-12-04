package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.util._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.util.DebugPrint

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

       }
    }


    //var marker = builder.mark()
   // rollForward
//    ParserUtils.rollForward(builder)

    //compilation unit
    if ( !builder.eof() ){
      CompilationUnit.parse(builder)
    }

    if (!builder.eof()) {
      val trashMarker = builder.mark
      while (!builder.eof()) {
        builder error "out of compilation unit"
        DebugPrint println ("after TopStatSeq: " + builder.getTokenType)
        builder.advanceLexer
      }
      trashMarker.done(ScalaElementTypes.TRASH)
    }
    /*
    parseNext
    ParserUtils.rollForward(builder)
    */
    //marker.done(ScalaElementTypes.FILE)
  }

}