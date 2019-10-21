package org.jetbrains.plugins.scala.editor.todo

import com.intellij.psi.impl.cache.impl.{BaseFilterLexer, OccurrenceConsumer}
import com.intellij.psi.search.UsageSearchContext
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}

/**
 * This basic implementation was only added to support proper TODO indexes for ScalaDoc
 *
 * see [[com.intellij.psi.impl.cache.impl.idCache.JavaFilterLexer]]
 */
final class ScalaFilterLexer(scalaLexer: ScalaLexer, consumer: OccurrenceConsumer)
  extends BaseFilterLexer(scalaLexer, consumer) {

  override def advance(): Unit = {
    val tokenType = myDelegate.getTokenType

    if (ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(tokenType)) {
      scanWordsInToken(UsageSearchContext.IN_COMMENTS, false, false)
      advanceTodoItemCountsInToken()
    }

    myDelegate.advance()
  }
}
