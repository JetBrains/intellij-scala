package org.jetbrains.plugins.scala
package annotator

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class SymbolLiteralAnnotatorTestBase extends ScalaAnnotatorQuickFixTestBase {
  val symbolName = "symb"

  override protected val description = ScalaBundle.message("symbolliterals.are.deprecated", symbolName)
}

class SymbolLiteralAnnotatorTest_Scala2 extends SymbolLiteralAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala2 && version >= LatestScalaVersions.Scala_2_13

  def hint = ScalaBundle.message("convert.to.explicit.symbol", symbolName)

  def test_in_assignment(): Unit = {
    val code =
      s"""
         |val test = $START'$symbolName$END
      """.stripMargin
    checkTextHasError(code)

    testQuickFix(
      code,
      s"""
         |val test = Symbol("$symbolName")
      """.stripMargin,
      hint
    )
  }
}

class SymbolLiteralAnnotatorTest_Scala3 extends SymbolLiteralAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def test_in_assignment(): Unit = {
    checkTextHasNoErrors(s"inline def foo(inline $symbolName: String) = $${ fooImpl('$symbolName) }")
  }
}
