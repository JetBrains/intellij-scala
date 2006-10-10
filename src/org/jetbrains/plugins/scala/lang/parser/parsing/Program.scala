package org.jetbrains.plugins.scala.lang.parser.parsing


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Top
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Literal

import com.intellij.lang.PsiBuilder

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 * Time: 12:53:26
 */

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
      while ( !builder.eof() ){
         rollForward
         builder.getTokenType match{
             case ScalaTokenTypes.tINTEGER
             | ScalaTokenTypes.tFLOAT
             | ScalaTokenTypes.kTRUE
             | ScalaTokenTypes.kFALSE
             | ScalaTokenTypes.tCHAR
             | ScalaTokenTypes.kNULL
             | ScalaTokenTypes.tSTRING_BEGIN
                    => new Literal parse(builder)
             case _ => builder advanceLexer
           }
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