package org.jetbrains.plugins.scala.lang.formatting.patterns

package indent {

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

}

package spacing {

  import com.intellij.psi.tree.TokenSet
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

  object SpacingTokens extends ScalaTokenTypes {

    val NO_SPACING_BEFORE = TokenSet.create(Array
      (
        ScalaTokenTypes.tDOT,
        ScalaTokenTypes.tCOMMA,
        ScalaTokenTypes.tSEMICOLON,
        ScalaTokenTypes.tRPARENTHIS,
        ScalaTokenTypes.tRSQBRACKET
      )
    )

    val SPACING_AFTER = TokenSet.create(
       Array(ScalaTokenTypes.tCOMMA)
    )

    val NO_SPACING_AFTER = TokenSet.create(
        Array(ScalaTokenTypes.tDOT,
              ScalaTokenTypes.tLPARENTHIS,
              ScalaTokenTypes.tLSQBRACKET
        )
    )

  }

}