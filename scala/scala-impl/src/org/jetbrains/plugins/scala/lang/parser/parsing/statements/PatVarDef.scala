package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations

/**
 * [[PatVarDef]] ::= [[Annotations]] {Modifier} ( 'val' PatDef | 'var' VarDef )
 *
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/
object PatVarDef extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder):Boolean = {
    val patVarMarker = builder.mark

    Annotations()(builder)

    //parse modifiers
    val modifierMarker = builder.mark
    while (Modifier()) {}
    modifierMarker.done(ScalaElementType.MODIFIERS)
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL =>
        builder.advanceLexer() //Ate val
        if (PatDef()) {
          patVarMarker.done(ScalaElementType.PATTERN_DEFINITION)
          true
        }
        else {
          patVarMarker.rollbackTo()
          false
        }
      case ScalaTokenTypes.kVAR =>
        builder.advanceLexer() //Ate var
        if (VarDef()) {
          patVarMarker.done(ScalaElementType.VARIABLE_DEFINITION)
          true
        }
        else {
          patVarMarker.rollbackTo()
          false
        }
      case _ =>
        patVarMarker.rollbackTo()
        false
    }
  }
}