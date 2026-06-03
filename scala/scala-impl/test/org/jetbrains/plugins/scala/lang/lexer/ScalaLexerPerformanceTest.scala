package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.PlatformTestUtil

import java.nio.file.Path

class ScalaLexerPerformanceTest extends ScalaLexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "performance")

  override protected def transform(testName: String, fileText: String): String = {
    var result = ""
    PlatformTestUtil.assertTiming(
      "Lexer performance test",
      1000,
      () => {
        result = super.transform(testName, fileText)
      }
    )
    result
  }

  override protected def onToken(lexer: Lexer, tokenType: IElementType, builder: StringBuilder): Unit = {}
}
