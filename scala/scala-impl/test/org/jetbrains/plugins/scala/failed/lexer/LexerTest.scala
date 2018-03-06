package org.jetbrains.plugins.scala.failed.lexer

import com.intellij.lexer.Lexer
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.junit.experimental.categories.Category


/**
  * @author mucianm 
  * @since 25.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class LexerTest extends SimpleTestCase {

  override protected def shouldPass: Boolean = false

  protected var lexer: Lexer = new ScalaLexer()

  def testSCL7878(): Unit = {
    assert(compare(
      "val x = ???//TODO",
        Seq("val", " ", "x", " ", "=", " ", "???", "//", "TODO")
      )
    )
  }

  protected def compare(text: String, tokens: Seq[String]): Boolean = {
    lexer.start(text)
    val buffer = scala.collection.mutable.ArrayBuffer[String]()
    while(lexer.getTokenType != null) {
      buffer += lexer.getTokenSequence.toString
      lexer.advance()
    }
    !shouldPass ^ buffer == tokens
  }
}
