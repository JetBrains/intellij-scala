package org.jetbrains.plugins.scala.lang.parser.parsing


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.lang.PsiBuilder

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 * Time: 12:53:26
 */

class Program extends ScalaTokenTypes {
  def parse(builder: PsiBuilder): Unit = {

    var marker = builder.mark()

//handle top level - package, import

    if ( !builder.eof() ){

      builder getTokenType match {
        case kPACKAGE => new Package parse(builder)
      }

    }

//other content in source file
/*
    while ( !builder.eof() ){

      builder getTokenType match {
        case kCLASS => Console.println("Class, odnako...")
      }


      builder.advanceLexer()
    }
*/
    marker.done(ScalaElementTypes.FILE)
  }
}