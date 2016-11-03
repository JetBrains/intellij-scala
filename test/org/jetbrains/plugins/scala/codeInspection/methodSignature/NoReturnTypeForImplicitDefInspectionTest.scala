package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.implicits.NoReturnTypeForImplicitDefInspection

/**
  * @author Nikolay.Tropin
  */
class NoReturnTypeForImplicitDefInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[NoReturnTypeForImplicitDefInspection]

  override protected def annotation: String = NoReturnTypeForImplicitDefInspection.description
  private val hint = NoReturnTypeForImplicitDefInspection.hint

  def testImplicitDef(): Unit = {
    val selected =
      s"""object Foo {
         |  ${START}implicit def bar(s: Int)$END = true
         |}""".stripMargin
    check(selected)
    val text =
      s"""object Foo {
         |  implicit ${CARET_MARKER}def bar(s: Int) = true
         |}""".stripMargin
    val result =
      s"""object Foo {
         |  implicit def bar(s: Int): Boolean = true
         |}""".stripMargin
    testFix(text, result, hint)
  }

  def testNotImplicitDef(): Unit = {
    checkTextHasNoErrors("""object Foo {
                           |  def bar(s: Int) = true
                           |}""".stripMargin)
  }

  def testWithReturnType(): Unit = {
    checkTextHasNoErrors("""object Foo {
                           |  implicit def bar(s: Int): Boolean = true
                           |}""".stripMargin)
  }

}
