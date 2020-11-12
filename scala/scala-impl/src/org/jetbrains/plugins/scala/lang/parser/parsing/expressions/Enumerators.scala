package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Guard
import org.jetbrains.plugins.scala.lang.parser.util.InScala3

/*
 * Enumerators ::= Generator {semi Enumerator | Guard}
 */
abstract class Enumerators(val isInIndentationRegion: Boolean) extends ParsingRule {

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val enumsMarker = builder.mark

    val blockIndentation =
      if (isInIndentationRegion) BlockIndentation.create
      else BlockIndentation.noBlock

    blockIndentation.fromHere()

    // eat all semicolons (which is not correct), show error in ScForAnnotator
    CommonUtils.eatAllSemicolons(blockIndentation)

    if (!Generator.parse(builder)) {
      blockIndentation.drop()
      enumsMarker.drop()
      return false
    }

    var continue = true
    while (continue) {
      blockIndentation.fromHere()
      val guard = builder.getTokenType match {
        case ScalaTokenTypes.tSEMICOLON =>
          builder.advanceLexer()
          // eat all semicolons (which is not correct), show error in ScForAnnotator
          CommonUtils.eatAllSemicolons(blockIndentation)
          false
        case ScalaTokenTypes.kCASE => false
        case InScala3(ScalaTokenTypes.kDO | ScalaTokenTypes.kYIELD) => continue = false; true
        case _ if builder.newlineBeforeCurrentToken => false
        case _ if Guard.parse(builder) => true
        case _ => continue = false; true
      }
      continue &&= (guard || Enumerator.parse(builder))
    }
    blockIndentation.drop()
    enumsMarker.done(ScalaElementType.ENUMERATORS)
    true
  }
}

object Enumerators extends Enumerators(isInIndentationRegion = false)

object EnumeratorsInIndentationRegion extends Enumerators(isInIndentationRegion = true)