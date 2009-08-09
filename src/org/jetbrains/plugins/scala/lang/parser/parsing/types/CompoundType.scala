package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.ScalaBundle

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 *  CompoundType ::= AnnotType {with AnnotType} [Refinement]
 *                 | Refinement
 */

object CompoundType {
  def parse(builder: PsiBuilder): Boolean = {
    val compoundMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR | ScalaTokenTypes.tLBRACE => {
        if (Refinement parse builder) {
          compoundMarker.done(ScalaElementTypes.COMPOUND_TYPE)
          return true
        }
        else {
          compoundMarker.drop
          return false
        }
      }
      case _ => {
        if (!AnnotType.parse(builder)) {
          compoundMarker.drop
          return false
        }
        else {
          var isCompound = false
          while (builder.getTokenType == ScalaTokenTypes.kWITH) {
            isCompound = true
            builder.advanceLexer //Ate with
            if (!AnnotType.parse(builder)) {
              builder error ScalaBundle.message("wrong.type")
            }
          }
          val hasRefinement = Refinement parse builder
          if (isCompound || hasRefinement) {
            compoundMarker.done(ScalaElementTypes.COMPOUND_TYPE)
          }
          else compoundMarker.drop
          return true
        }
      }
    }
  }
}