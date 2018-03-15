package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.util.Setter

class InlayTypeHintsTest extends InlayHintsTestBase {

  //  import InlayHintsTestBase.{HintEnd => E, HintStart => S}
  import ScalaCodeInsightSettings.{getInstance => settings}

  //  def testFunctionReturnTypeHint(): Unit = doTest(
  //    s"""  def foo()$S: List[String]$E = List.empty[String]"""
  //  )

  def testNoFunctionReturnTypeHint(): Unit = doTest(
    """  def foo(): List[String] = List.empty[String]"""
  )

  def testNoConstructorReturnTypeHint(): Unit = doTest(
    """  def this(foo: Int) = this()"""
  )

  //  def testPropertyTypeHint(): Unit = doTest(
  //    s"""  val list$S: List[String]$E = List.empty[String]""",
  //    setter = settings.showPropertyTypeSetter
  //  )

  def testNoPropertyTypeHint(): Unit = doTest(
    """  val list: List[String] = List.empty[String]""",
    setter = settings.showPropertyTypeSetter
  )

  //  def testLocalVariableTypeHint(): Unit = doTest(
  //    s"""  def foo(): Unit = {
  //       |    val list$S: List[String]$E = List.empty[String]
  //       |  }""".stripMargin,
  //    setter = settings.showLocalVariableTypeSetter
  //  )

  def testNoLocalVariableTypeHint(): Unit = doTest(
    s"""  def foo(): Unit = {
       |    val list: List[String] = List.empty[String]
       |  }""".stripMargin,
    setter = settings.showLocalVariableTypeSetter
  )

  private def doTest(text: String, setter: Setter[java.lang.Boolean]): Unit =
    super.doTest(text, setter.set(_))
}
