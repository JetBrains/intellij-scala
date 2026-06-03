package org.jetbrains.plugins.scala.editor.todo

import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import com.intellij.psi.impl.cache.impl.{BaseFilterLexer, OccurrenceConsumer, idCache}
import com.intellij.psi.search.UsageSearchContext.IN_COMMENTS
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}

final class ScalaTodoIndexer extends LexerBasedTodoIndexer {

  override def createLexer(consumer: OccurrenceConsumer): BaseFilterLexer =
    new ScalaTodoIndexer.ScalaFilterLexer(consumer)
}

object ScalaTodoIndexer {

  /**
   * This basic implementation was only added to support proper TO-DO indices for ScalaDoc
   *
   * see [[idCache.JavaFilterLexer]]
   */
  private final class ScalaFilterLexer(consumer: OccurrenceConsumer)
    extends BaseFilterLexer(new ScalaLexer(false, null), consumer) {

    override def advance(): Unit = {
      val tokenType = getDelegate.getTokenType

      if (ScalaTokenTypes.COMMENTS_TOKEN_SET.contains(tokenType)) {
        scanWordsInToken(IN_COMMENTS, false, false)
        advanceTodoItemCountsInToken()
      }

      getDelegate.advance()
    }
  }
}