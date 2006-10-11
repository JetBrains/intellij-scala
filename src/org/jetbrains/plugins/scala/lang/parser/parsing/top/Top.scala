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

class Top extends ScalaTokenTypes{
  def parse(builder: PsiBuilder): Unit = {

//handle PACKAGE
    if ( builder.getTokenType == ScalaTokenTypes.kPACKAGE) {
      new Package().parse(builder)
    }
/*
//handle IMPORT LIST
    if ( builder.getTokenType == ScalaTokenTypes.kIMPORT) {
      val importListMarker = builder.mark()
      importListMarker.done( ScalaElementTypes.IMPORT_LIST )

      new ImportList().parse(builder)
      //builder.advanceLexer()
    }    
    
  }
*/
  }

}