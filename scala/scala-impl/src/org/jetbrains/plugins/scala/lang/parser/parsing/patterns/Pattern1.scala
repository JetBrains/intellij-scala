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
 */
object Pattern1 extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean =
    if (builder.isScala3) parseInScala3() else parseInScala2()

  private def parseInScala3()(implicit builder: ScalaPsiBuilder): Boolean = {
    val pattern1Marker = builder.mark()
    val pattern2Parsed = Pattern2()
    if (pattern2Parsed && builder.getTokenType == tCOLON) {
      builder.advanceLexer() //Ate :
      if (!TypePattern()) {
        builder.error(ScalaBundle.message("wrong.type"))
      }
      pattern1Marker.done(ScalaElementType.SCALA3_TYPED_PATTERN)
      true
    } else {
      pattern1Marker.drop()
      pattern2Parsed
    }
  }

  private def parseInScala2()(implicit builder: ScalaPsiBuilder): Boolean = {
    val pattern1Marker = builder.mark()
    if (PatVar()) {
      builder.getTokenType match {
        case `tCOLON` =>
          builder.advanceLexer() //Ate :
          if (!TypePattern()) {
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
    Pattern2()
  }
}