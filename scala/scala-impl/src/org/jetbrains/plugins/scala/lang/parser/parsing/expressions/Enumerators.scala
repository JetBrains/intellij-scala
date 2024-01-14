package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.{CommonUtils, ParsingRule}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Guard
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

/*
 * Enumerators ::= Generator {semi Enumerator | Guard}
 */
object Enumerators extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val enumsMarker = builder.mark()

    // eat all semicolons (which is not correct), show error in ScForAnnotator
    CommonUtils.eatAllSemicolons()

    if (!Generator()) {
      enumsMarker.drop()
      return false
    }

    var continue = true
    while (continue) {
      val guard = builder.getTokenType match {
        case ScalaTokenTypes.tSEMICOLON =>
          builder.advanceLexer()
          // eat all semicolons (which is not correct), show error in ScForAnnotator
          CommonUtils.eatAllSemicolons()
          false
        case ScalaTokenTypes.kCASE => false
        case InScala3(ScalaTokenTypes.kDO | ScalaTokenTypes.kYIELD) => continue = false; true
        case _ if builder.newlineBeforeCurrentToken => false
        case _ if Guard() => true
        case _ => continue = false; true
      }
      continue &&= (guard || Enumerator())
    }
    enumsMarker.done(ScalaElementType.ENUMERATORS)
    true
  }
}