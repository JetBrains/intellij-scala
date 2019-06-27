package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

class SymbolLiteralAnnotatorTest extends ScalaAnnotatorQuickFixTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13

  val symbolName = "symb"

  override protected val description = ScalaBundle.message("symbolliterals.are.deprecated", symbolName)
  def hint = ScalaBundle.message("convert.to.explicit.symbol", symbolName)

  def test_in_assignment(): Unit = {
    val code =
      s"""
         |val test = <selection>'$symbolName</selection>
      """
    checkTextHasError(code)

    testQuickFix(
      code,
      s"""
         |val test = Symbol("$symbolName")
      """,
      hint
    )
  }
}
