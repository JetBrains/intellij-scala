package org.jetbrains.plugins.scala.lang.formatting.patterns

package indent {

  import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
  /**
  *  For blocks & composite expressions
  */
  trait BlockedIndent

  /**
  *  For template declarations and definitions
  */
  trait TemplateIndent

  /**
  *  For parameter lists
  */
  trait ContiniousIndent

  /**
  *  For Continuous expressions
  */
  trait IfElseIndent

  /**
  *   For conditions
  */
  trait CondIndent {
    def condition: ScalaExpression
  }


}

package spacing {


  import com.intellij.psi.tree.TokenSet
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
  import org.jetbrains.plugins.scala.lang.parser._
  import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
  import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter

  object SpacingTokens extends ScalaTokenTypes {

    val NO_SPACING_BEFORE = TokenSet.create(Array(ScalaTokenTypes.tDOT,
            ScalaTokenTypes.tCOMMA,
            ScalaTokenTypes.tSEMICOLON,
            ScalaTokenTypes.tCOLON,
            ScalaTokenTypes.tRPARENTHESIS,
            ScalaTokenTypes.tRSQBRACKET))

    val SPACING_AFTER = TokenSet.orSet(Array(TokenSet.create(Array(ScalaTokenTypes.tCOMMA,
            ScalaTokenTypes.tCOLON,
            ScalaTokenTypes.tSEMICOLON)),
            ScalaSyntaxHighlighter.kRESWORDS))

    val NO_SPACING_AFTER = TokenSet.create(Array(ScalaTokenTypes.tDOT,
            ScalaTokenTypes.tLPARENTHESIS,
            ScalaTokenTypes.tLSQBRACKET))

    val SINGLE_SPACING_BETWEEN = TokenSet.create(Array(ScalaElementTypes.INFIX_EXPR,
            ScalaTokenTypes.tIDENTIFIER,
            ScalaElementTypes.PATTERN3))

    val SPACING_BEFORE = TokenSet.create(Array(ScalaTokenTypes.tASSIGN))

  }

}