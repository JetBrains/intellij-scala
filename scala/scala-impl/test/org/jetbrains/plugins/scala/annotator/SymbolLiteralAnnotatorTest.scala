package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_13}

class SymbolLiteralAnnotatorTest extends ScalaAnnotatorQuickFixTestBase {

  override implicit val version: ScalaVersion = Scala_2_13

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
