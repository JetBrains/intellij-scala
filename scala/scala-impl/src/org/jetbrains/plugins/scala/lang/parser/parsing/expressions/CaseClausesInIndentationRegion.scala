package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.{CaseClausesWithoutBraces, ExprCaseClause}

abstract class CaseClausesInIndentationRegion extends ParsingRule {

  /* catch can be directly followed by case in the same line
   * in that case there must be exactly one expression in the case's body
   * ```
   * try test()
   * catch case NonFatal(e) => println(e)
   */
  def allowExprCaseClause: Boolean

  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    if (!builder.isScala3 || builder.getTokenType != ScalaTokenTypes.kCASE) {
      return false
    }

    builder.findPreviousIndent match {
      case Some(indentationWidth) =>
        builder.withIndentationWidth(indentationWidth) {
          CaseClausesWithoutBraces()
        }
      case None if allowExprCaseClause =>
        // Something like
        // try test() catch case _ =>
        ExprCaseClause()
      case None =>
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