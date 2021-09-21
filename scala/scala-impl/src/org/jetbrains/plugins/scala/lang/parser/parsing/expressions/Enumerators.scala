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

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val enumsMarker = builder.mark()

    val blockIndentation =
      if (isInIndentationRegion) BlockIndentation.create
      else BlockIndentation.noBlock

    blockIndentation.fromHere()

    // eat all semicolons (which is not correct), show error in ScForAnnotator
    CommonUtils.eatAllSemicolons(blockIndentation)

    if (!Generator()) {
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
        case _ if Guard() => true
        case _ => continue = false; true
      }
      continue &&= (guard || Enumerator())
    }
    blockIndentation.drop()
    enumsMarker.done(ScalaElementType.ENUMERATORS)
    true
  }
}

object Enumerators extends Enumerators(isInIndentationRegion = false)

/**
 * TODO: for poorly-indented enumerators we could show an error in annotator (but still parse the enumerators):
 *  {{{
 *    for
 *        x <- 1 to 2
 *      y <- 1 to 2
 *    yield x + y
 *  }}}
 *  NOTE: with `-no-indent` flag this code is parsed OK and no error should be shown.
 *  (note that braces are still optional, they are disabled with `-old-syntax` flag)
 *  see https://github.com/lampepfl/dotty/issues/12427#issuecomment-839654212
 */
object EnumeratorsInIndentationRegion extends Enumerators(isInIndentationRegion = true)