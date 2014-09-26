package org.jetbrains.plugins.scala
package lang.findUsages

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer
import com.intellij.psi.impl.cache.impl.{BaseFilterLexer, OccurrenceConsumer}
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes => tokens}

/**
 * Nikolay.Tropin
 * 2014-09-24
 */
class ScalaIdIndexer extends LexerBasedIdIndexer {
  override def createLexer(consumer: OccurrenceConsumer): Lexer = new ScalaFilterLexer(consumer)

  class ScalaFilterLexer(consumer: OccurrenceConsumer) extends BaseFilterLexer(new ScalaLexer(), consumer) {

    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
    val toSkipSet = TokenSet.orSet(WHITES_SPACES_TOKEN_SET,
      TokenSet.create(tLBRACE, tRBRACE, tLPARENTHESIS, tRPARENTHESIS, tLOWER_BOUND, tUPPER_BOUND,
        tCOMMA, tDOT, tSEMICOLON, tLSQBRACKET, tRSQBRACKET)
    )

    override def advance() {
      val tokenType = myDelegate.getTokenType

      tokenType match {
        case tokens.tIDENTIFIER | tokens.tCHAR | tokens.tINTEGER | tokens.tFLOAT =>
          addOccurrenceInToken(UsageSearchContext.IN_CODE)
        case t if tokens.STRING_LITERAL_TOKEN_SET.contains(t) || t == tokens.tSYMBOL =>
          scanWordsInToken(UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_FOREIGN_LANGUAGES, false, true)
        case t if tokens.COMMENTS_TOKEN_SET.contains(t) =>
          scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false)
          advanceTodoItemCountsInToken()
        case t if !toSkipSet.contains(tokenType) =>
          scanWordsInToken(UsageSearchContext.IN_PLAIN_TEXT, false, false)
        case _ =>
      }
      myDelegate.advance()
    }
  }

}

