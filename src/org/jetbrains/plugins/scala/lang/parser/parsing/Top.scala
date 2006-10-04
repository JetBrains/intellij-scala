package org.jetbrains.plugins.scala.lang.parser.parsing


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.10.2006
 * Time: 18:08:23
 */

class Top extends ScalaTokenTypes {
  def parse(builder: PsiBuilder): Unit = {
    var marker = builder.mark()

//sTUB have to be changed to LineTerminator+Line_IN_term
    while ( !builder.eof() && builder.getTokenType().equals( new ScalaTokenTypes().sTUB ) ) {
        builder.advanceLexer()
    }
    new Package().parse(builder)

    //marker.done(ScalaElementTypes.PACKAGE)
  }
}