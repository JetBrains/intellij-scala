package org.jetbrains.plugins.scala.lang.formatting.patterns

import lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.lexer._
import com.intellij.psi.tree._
import org.jetbrains.plugins.scala.lang.parser._

object SpacingTokens extends ScalaTokenTypes {

  val NO_SPACING_BEFORE = TokenSet.create(Array(ScalaTokenTypes.tDOT,
          ScalaTokenTypes.tCOMMA,
          ScalaTokenTypes.tSEMICOLON,
          ScalaTokenTypes.tCOLON,
          ScalaTokenTypes.tRPARENTHESIS,
          ScalaTokenTypes.tRSQBRACKET))

  val SPACING_AFTER = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.tCOMMA,
          ScalaTokenTypes.tCOLON,
          ScalaTokenTypes.tSEMICOLON))))

  val NO_SPACING_AFTER = TokenSet.create(Array(ScalaTokenTypes.tDOT,
          ScalaTokenTypes.tLPARENTHESIS,
          ScalaTokenTypes.tLSQBRACKET))

  val SINGLE_SPACING_BETWEEN = TokenSet.create(Array(ScalaElementTypes.INFIX_EXPR,
          ScalaTokenTypes.tIDENTIFIER,
          ScalaElementTypes.INFIX_PATTERN))

  val SPACING_BEFORE = TokenSet.create(Array(ScalaTokenTypes.tASSIGN))

}
