package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Setter

class InlayTypeHintsTest extends InlayHintsTestBase {

  import InlayHintsTestBase.{HintEnd => E, HintStart => S}
  import ScalaCodeInsightSettings.{getInstance => settings}

  def testFunctionReturnTypeHint(): Unit = doTest(
    s"""  def foo()$S: List[String]$E = List.empty[String]"""
  )

  def testNoFunctionReturnTypeHint(): Unit = doTest(
    """  def foo(): List[String] = List.empty[String]"""
  )

  def testNoConstructorReturnTypeHint(): Unit = doTest(
    """  def this(foo: Int) = this()"""
  )

  def testPropertyTypeHint(): Unit = doTest(
    s"""  val list$S: List[String]$E = List.empty[String]""",
    options = settings.showPropertyTypeSetter()
  )

  def testNoPropertyTypeHint(): Unit = doTest(
    """  val list: List[String] = List.empty[String]""",
    options = settings.showPropertyTypeSetter()
  )

  def testLocalVariableTypeHint(): Unit = doTest(
    s"""  def foo(): Unit = {
       |    val list$S: List[String]$E = List.empty[String]
       |  }""".stripMargin,
    options = settings.showLocalVariableTypeSetter()
  )

  def testNoLocalVariableTypeHint(): Unit = doTest(
    s"""  def foo(): Unit = {
       |    val list: List[String] = List.empty[String]
       |  }""".stripMargin,
    options = settings.showLocalVariableTypeSetter()
  )

  private def doTest(text: String, options: Setter[java.lang.Boolean]*): Unit = {
    def setOptions(value: Boolean): Unit = options.foreach(_.set(value))

    try {
      setOptions(true)

      configureFromFileText(text)
      getFixture.testInlays(
        inlayText(_).get,
        inlayText(_).isDefined
      )
    } finally {
      setOptions(false)
    }
  }

  private val inlayText = (_: Inlay[_]).getRenderer match {
    case renderer: HintRenderer => Some(renderer.getText)
    case _ => None
  }

}
