package org.jetbrains.plugins.scala.lang.parser.parsing

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
/**
 * User: Dmitry.Krasilschikov
 * Date: 03.10.2006
 * Time: 18:15:12
 */

class QualId {
  def parse(builder : PsiBuilder) : Unit = {
    /*
    val marker = builder.mark();
    marker.drop();

      //todo: not sTUB, but LineTerminator
    while ( !builder.getTokenTypes().equals() ) {

    }

    marker.done(new ScalaElementTypes().QUALID);
    */
  }
}