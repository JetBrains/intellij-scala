package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.types.TypeArgs
import org.jetbrains.plugins.scala.lang.parser.util.PrecedenceClimbingInfixParsingRule

/*
 * InfixExpr ::= PrefixExpr
 *             | InfixExpr id [TypeArgs] [nl] InfixExpr
 *             | InfixExpr id ColonArgument -- scala 3 fewer braces
 *             | InfixExpr MatchClause -- scala 3 new match expr. parsing rule
 */
object InfixExpr extends PrecedenceClimbingInfixParsingRule {
  override protected def parseFirstOperand()(implicit builder: ScalaPsiBuilder): Boolean = PrefixExpr()

  override protected def parseOperand()(implicit builder: ScalaPsiBuilder): Boolean =
    ColonArgument(needArgNode = false) || parseFirstOperand()

  override protected def referenceElementType: IElementType = ScalaElementType.REFERENCE_EXPRESSION
  override protected def infixElementType: IElementType = ScalaElementType.INFIX_EXPR
  override protected def isMatchConsideredInfix: Boolean = true

  override protected def parseAfterOperatorId(opMarker: PsiBuilder.Marker)(implicit builder: ScalaPsiBuilder): Unit =
    if (TypeArgs(isPattern = false)) {
      opMarker.precede().done(ScalaElementType.GENERIC_CALL)
    }
}
