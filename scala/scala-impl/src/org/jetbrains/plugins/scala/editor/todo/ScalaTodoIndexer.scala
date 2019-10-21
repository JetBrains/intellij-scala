package org.jetbrains.plugins.scala.editor.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import com.intellij.psi.impl.cache.impl.{IdAndToDoScannerBasedOnFilterLexer, OccurrenceConsumer}
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinitionBase

/** see [[com.intellij.psi.impl.cache.impl.idCache.JavaTodoIndexer]] */
final class ScalaTodoIndexer extends LexerBasedTodoIndexer with IdAndToDoScannerBasedOnFilterLexer {

  override def createLexer(consumer: OccurrenceConsumer): Lexer = {
    val scalaLexer = ScalaParserDefinitionBase.createLexer
    new ScalaFilterLexer(scalaLexer, consumer)
  }
}