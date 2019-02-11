package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Inlay
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
    options = settings.showPropertyTypeSetter(), settings.showObviousTypeSetter()
  )

  def testNoPropertyTypeHint(): Unit = doTest(
    """  val list: List[String] = List.empty[String]""",
    options = settings.showPropertyTypeSetter(), settings.showObviousTypeSetter()
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

  private def doTest(text: String, options: Setter[java.lang.Boolean]*): Unit = {
    def setOptions(value: Boolean): Unit = options.foreach(_.set(value))

    try {
      setOptions(true)
      doInlayTest(text)
    } finally {
      setOptions(false)
    }
  }

  private val inlayText = (_: Inlay[_]).getRenderer match {
    case renderer: HintRenderer => Some(renderer.getText)
    case _ => None
  }

}
