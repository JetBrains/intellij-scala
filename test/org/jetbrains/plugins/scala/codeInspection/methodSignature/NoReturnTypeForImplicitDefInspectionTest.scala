package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.implicits.NoReturnTypeForImplicitDefInspection

/**
  * @author Nikolay.Tropin
  */
class NoReturnTypeForImplicitDefInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[NoReturnTypeForImplicitDefInspection]

  override protected val description: String =
    NoReturnTypeForImplicitDefInspection.description

  private val hint = NoReturnTypeForImplicitDefInspection.hint

  def testImplicitDef(): Unit = {
    val selected =
      s"""object Foo {
         |  ${START}implicit def bar(s: Int)$END = true
         |}""".stripMargin
    checkTextHasError(selected)
    val text =
      s"""object Foo {
         |  implicit ${CARET_MARKER}def bar(s: Int) = true
         |}""".stripMargin
    val result =
      s"""object Foo {
         |  implicit def bar(s: Int): Boolean = true
         |}""".stripMargin
    testQuickFix(text, result, hint)
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
