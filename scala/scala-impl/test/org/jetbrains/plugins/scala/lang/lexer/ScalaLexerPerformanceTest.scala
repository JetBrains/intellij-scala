package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.tools.ide.metrics.benchmark.Benchmark

import java.nio.file.Path

@PerformanceUnitTest
class ScalaLexerPerformanceTest extends ScalaLexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "performance")

  override protected def transform(testName: String, fileText: String): String = {
    var result = ""
    Benchmark.newBenchmark(
      "Lexer performance test",
      () => {
        result = super.transform(testName, fileText)
      }
    ).start()
    result
  }

  override protected def onToken(lexer: Lexer, tokenType: IElementType, builder: StringBuilder): Unit = {}
}
