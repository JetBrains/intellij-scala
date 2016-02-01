package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

/*
 *  CompoundType ::= AnnotType {with AnnotType} [Refinement]
 *                 | Refinement
 */

object CompoundType extends Type {
  override protected val infixType = InfixType

  override def parse(builder: ScalaPsiBuilder, star: Boolean, isPattern: Boolean): Boolean = {
    val compoundMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        if (Refinement parse builder) {
          compoundMarker.done(ScalaElementTypes.COMPOUND_TYPE)
          true
        } else {
          compoundMarker.drop()
          false
        }
      case _ =>
        if (!AnnotType.parse(builder, isPattern)) {
          compoundMarker.drop()
          false
        } else {
          var isCompound = false
          while (builder.getTokenType == ScalaTokenTypes.kWITH) {
            isCompound = true
            builder.advanceLexer() //Ate with
            if (!AnnotType.parse(builder, isPattern)) {
              builder error ScalaBundle.message("wrong.type")
            }
          }
          val hasRefinement = Refinement parse builder
          if (isCompound || hasRefinement) {
            compoundMarker.done(ScalaElementTypes.COMPOUND_TYPE)
          } else compoundMarker.drop()
          true
        }
    }
  }
}