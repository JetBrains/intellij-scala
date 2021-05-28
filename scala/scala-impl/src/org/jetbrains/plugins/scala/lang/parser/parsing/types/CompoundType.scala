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
  override protected def infixType: InfixType = InfixType

  override def parse(builder: ScalaPsiBuilder, star: Boolean, isPattern: Boolean): Boolean = {
    val compoundMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        if (Refinement parse builder) {
          compoundMarker.done(ScalaElementType.COMPOUND_TYPE)
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
          while (builder.getTokenType == ScalaTokenTypes.kWITH || (builder.isSource3Enabled && builder.getTokenText == "&")) {
            isCompound = true
            builder.advanceLexer() //Ate with or & (only in -Xsource:3)
            if (!AnnotType.parse(builder, isPattern)) {
              builder error ScalaBundle.message("wrong.type")
            }
          }
          val hasRefinement = Refinement parse builder
          if (isCompound || hasRefinement) {
            compoundMarker.done(ScalaElementType.COMPOUND_TYPE)
          } else compoundMarker.drop()
          true
        }
    }
  }
}