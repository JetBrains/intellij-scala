package org.jetbrains.plugins.scala.lang.parser.parsing.top


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.10.2006
 * Time: 18:08:23
 */

class Top {
  def parse(builder: PsiBuilder): Unit = {

//handle PACKAGE
    if ( builder.getTokenType == ScalaTokenTypes.kPACKAGE) {
      new Package().parse(builder)
      //builder.advanceLexer()
    }

//handle IMPORT
    while ( builder.getTokenType == ScalaTokenTypes.kIMPORT) {
      new Import().parse(builder)
      //builder.advanceLexer()
    }
    
  }
}