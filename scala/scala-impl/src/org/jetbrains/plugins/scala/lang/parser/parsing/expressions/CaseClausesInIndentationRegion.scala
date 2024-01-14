package org.jetbrains.plugins.scala.lang.parser.parsing.expressions
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.{CaseClausesWithoutBraces, ExprCaseClause}

abstract class CaseClausesInIndentationRegion extends ParsingRule {

  /** catch can be directly followed by case in the same line
   * in that case there must be exactly one expression in the case's body
   * {{{
   * try test()
   * catch case NonFatal(e) => println(e)
   * }}}
   */
  def allowExprCaseClause: Boolean

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3  || builder.getTokenType != ScalaTokenTypes.kCASE) {
      return false
    }

    // NOTE: according to the specification new control syntax for `catch case` is only allowed when `case`
    // goes on the same line with `catch`. It is allowed even if indentation-based syntax is disabled.
    // If indentation-based syntax is disabled `case` is not allowed on a new line without braces.
    // However, for some reason scala-compiler still can parse `case` on new line
    // This behaviour is different in our parser
    // see https://github.com/lampepfl/dotty/issues/11905#issuecomment-808316102

    // we are at `case`
    if (builder.isScala3IndentationBasedSyntaxEnabled && builder.hasPrecedingIndent && !builder.isOutdentHere) {
      builder.withIndentationRegion(builder.newBracelessIndentationRegionHere) {
        CaseClausesWithoutBraces()
      }
    } else if (allowExprCaseClause) {
      // Something like
      // try test() catch case _ =>
      ExprCaseClause()
    } else {
      false
    }
  }
}

object CaseClausesInIndentationRegion extends CaseClausesInIndentationRegion {
  override def allowExprCaseClause: Boolean = false
}

object CaseClausesOrExprCaseClause extends CaseClausesInIndentationRegion {
  override def allowExprCaseClause: Boolean = true
}