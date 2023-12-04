package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.util.Setter

class InlayTypeHintsTest extends InlayHintsTestBase {

  import Hint.{End => E, Start => S}
  import ScalaCodeInsightSettings.{getInstance => settings}

  def testFunctionReturnTypeHint(): Unit = doTest(
    s"""  def foo()$S: List[String]$E = List.empty[String]""",
    options = settings.showObviousTypeSetter()
  )

  def testNoFunctionReturnTypeHint(): Unit = doTest(
    """  def foo(): List[String] = List.empty[String]"""
  )

  def testNoConstructorReturnTypeHint(): Unit = doTest(
    """  def this(foo: Int) = this()"""
  )

  def testPropertyTypeHint(): Unit = doTest(
    s"""  val list$S: List[String]$E = List.empty[String]""",
    options = settings.showMemberVariableSetter(), settings.showObviousTypeSetter()
  )

  def testNoPropertyTypeHint(): Unit = doTest(
    """  val list: List[String] = List.empty[String]""",
    options = settings.showMemberVariableSetter(), settings.showObviousTypeSetter()
  )

  def testLocalVariableTypeHint(): Unit = doTest(
    s"""  def foo(): Unit = {
       |    val list$S: List[String]$E = List.empty[String]
       |  }""".stripMargin,
    options = settings.showLocalVariableTypeSetter(), settings.showObviousTypeSetter()
  )

  def testNoLocalVariableTypeHint(): Unit = doTest(
    s"""  def foo(): Unit = {
       |    val list: List[String] = List.empty[String]
       |  }""".stripMargin,
    options = settings.showLocalVariableTypeSetter(), settings.showObviousTypeSetter()
  )

  def testConstructorObviousTypeHint(): Unit = doTest(
    s"  def text$S: String$E = new String",
    options = settings.showObviousTypeSetter()
  )

  def testConstructorNoTypeHint(): Unit = doTest(
    s"  def text = new String"
  )

  def testLiteralObviousTypeHint(): Unit = doTest(
    s"  def int$S: Int$E = 0",
    options = settings.showObviousTypeSetter()
  )

  def testLiteralNoTypeHint(): Unit = doTest(
    s"  def int = 0"
  )

  def testEmptyCollectionNoTypeHint(): Unit = doTest(
    s"""  def foo() = List.empty[String]"""
  )

  def testPreserveIndentSingleWhitespaces(): Unit = doTest(
    s"  val v$S: Int$E = 1",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentNoWhitespaces(): Unit = doTest(
    s"  val v$S: Int$E=1",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentAdditionalWhitespacesBeforeEquals(): Unit = doTest(
    s"  val v  = 123",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentAdditionalWhitespacesAfterEquals(): Unit = doTest(
    s"  val v =  123",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentGroup(): Unit = doTest(
    s"  val a$S: Int$E = 1\n  val b$S: Int$E = 2",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentGroupBefore(): Unit = doTest(
    s"  val a =  1\n  val b = 2",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentGroupAfter(): Unit = doTest(
    s"  val a = 1\n  val b =  2",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentNewLine(): Unit = doTest(
    s"  val b$S: Int$E =\n    1",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentEmptyLineBefore(): Unit = doTest(
    s"  val a =  1\n\n  val b$S: Int$E = 2",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentEmptyLineAfter(): Unit = doTest(
    s"  val a$S: Int$E = 1\n\n  val b =  2",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentSemicolonBefore(): Unit = doTest(
    s"  val a =  1; val b$S: Int$E = 2",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testPreserveIndentSemicolonAfter(): Unit = doTest(
    s"  val a$S: Int$E = 1; val b =  2",
    options = settings.showMemberVariableSetter, settings.preserveIndentsSetter, settings.showObviousTypeSetter
  )

  def testNavigationTooltip(): Unit = doTest(
    s"""  val a$S: Foo /* [light_idea_test_case] default\\nclass Foo extends <span style='color:#000000;'><a href='psi_element://java.lang.Object'><code>Object</code></a></span> */ $E = new Foo()""".stripMargin,
    withTooltips = true,
    options = settings.showMemberVariableSetter, settings.showObviousTypeSetter
  )

  private def doTest(text: String, options: Setter[java.lang.Boolean]*): Unit =
    doTest(text, false, options: _*)

  private def doTest(text: String, withTooltips: Boolean, options: Setter[java.lang.Boolean]*): Unit = {
    def setOptions(value: Boolean): Unit = options.foreach(_.set(value))

    try {
      setOptions(true)
      doInlayTest(text, withTooltips)
    } finally {
      setOptions(false)
    }
  }
}
