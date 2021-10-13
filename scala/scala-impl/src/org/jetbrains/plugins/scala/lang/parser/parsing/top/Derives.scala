package org.jetbrains.plugins.scala.lang.parser
package parsing
package top
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

object Derives extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    assert(builder.isScala3)

    val marker = builder.mark()
    if (builder.tryParseSoftKeyword(ScalaTokenType.DerivesKeyword)) {
      parseNext()
      marker.done(ScalaElementType.DERIVES_CLAUSE)
    } else marker.drop()

    true
  }

  @tailrec
  private def parseNext()(implicit builder: ScalaPsiBuilder): Unit = {
    if (!QualId()) {
      builder.error(ScalaBundle.message("identifier.expected"))
    }

    if (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
      builder.advanceLexer() // ate ,
      parseNext()
    }
  }
}
