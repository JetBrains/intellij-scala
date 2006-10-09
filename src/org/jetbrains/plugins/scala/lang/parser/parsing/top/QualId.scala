package org.jetbrains.plugins.scala.lang.parser.parsing.top

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

    val marker = builder.mark()   // new marker for qualifier id
    builder.advanceLexer   // read QualID identifier


    builder.getTokenType match {
      case ScalaTokenTypes.tDOT => {
        builder.advanceLexer
        new QualId parse(builder)        
      }

      case ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE => builder.advanceLexer
      case ScalaTokenTypes.tSEMICOLON => builder.advanceLexer
      case _ => builder.error("Wrong import name declaration");
    }

    marker.done(ScalaElementTypes.QUALID); // Close marker for qualID

  }
}