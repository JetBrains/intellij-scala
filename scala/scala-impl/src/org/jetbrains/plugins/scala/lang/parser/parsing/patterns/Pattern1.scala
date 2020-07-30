package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.tCOLON
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
 * Scala 2
 * [[Pattern1]] ::= [[PatVar]] ':' [[TypePattern]]
 * | [[Pattern2]]
 *
 * Scala 3
 * [[Pattern1]] ::= [[PatVar]] [':' [[RefinedType]] ]
 * | 'given' [[PatVar]] ':' [[RefinedType]]
 *
 * @author Alexander Podkhalyuzin
 *         Date: 28.02.2008
 */
object Pattern1 extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val pattern1Marker = builder.mark()
    if (PatVar()) {
      builder.getTokenType match {
        case `tCOLON` =>
          builder.advanceLexer() //Ate :
          if (!TypePattern.parse(builder)) {
            builder.error(ScalaBundle.message("wrong.type"))
          }
          pattern1Marker.done(ScalaElementType.TYPED_PATTERN)
          return true
        case _ =>
          pattern1Marker.rollbackTo()
      }
    } else {
      pattern1Marker.drop()
    }
    Pattern2.parse(builder, forDef = false)
  }
}