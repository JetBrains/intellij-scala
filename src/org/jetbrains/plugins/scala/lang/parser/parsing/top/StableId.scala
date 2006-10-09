package org.jetbrains.plugins.scala.lang.parser.parsing.top;

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 22:13:17
 */
class StableId {

  def parse(builder : PsiBuilder) : Unit = {

    val marker = builder.mark()
    builder.advanceLexer

    Console.println("token in stableID : " + builder.getTokenType)
    builder.getTokenType match {
      case ScalaTokenTypes.tDOT => {
        builder.advanceLexer
        new StableId parse(builder)
      }

     /* case ScalaTokenTypes.tCOMMA => {
        new Import parse(builder)
      }*/

      case ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE => builder.advanceLexer
      case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer
      case _ => builder.error("error in stableID");
    }

    marker.done(ScalaElementTypes.STABLE_ID); // Close marker for qualID

  }
}
