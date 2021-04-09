package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}

class EmptyParenMethodCallInspectionTest extends ScalaQuickFixTestBase {
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ParameterlessAccessInspection.EmptyParenMethod]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.parameterless.access.empty.paren")

  private val hint = ScalaInspectionBundle.message("add.call.parentheses")


  def test_call_without_parenthesis(): Unit = {
    getFixture.configureByText("S.scala",
      """
        |class S {
        |  def test(): Unit = ()
        |}
      """.stripMargin
    )

    checkTextHasError(
      text =
        s"""
           |new S().${START}test$END
         """.stripMargin
    )

    testQuickFix(
      text =
        s"""
           |new S().tes${CARET}t
         """.stripMargin,
      expected =
        s"""
           |new S().test()
         """.stripMargin,
      hint
    )
  }

  def test_ok(): Unit = {
    checkTextHasNoErrors(
      text =
        s"""
           |class S {
           |  def foo: Int = 0
           |}
           |
           |new S().foo
         """.stripMargin
    )

    checkTextHasNoErrors(
      text =
        s"""
           |class S {
           |  def foo(): Int = 0
           |}
           |
           |new S().foo()
         """.stripMargin
    )
  }

  def test_simple_in_object(): Unit = {
    checkTextHasError(
      s"""
         |def foo(): Int = 1
         |${START}foo$END
         |""".stripMargin
    )
  }

  def test_eta_expand_in_call(): Unit = {
    checkTextHasNoErrors(
      """
        |def foo(): Int = 1
        |def goo(x: () => Int) = 1
        |goo(foo) // okay
        |""".stripMargin
    )
  }

  def test_eta_expand_with_type_annotation(): Unit = {
    checkTextHasNoErrors(
      """
        |def foo(): Int = 1
        |foo : () => Int // okay
        |""".stripMargin
    )
  }

  def test_parenless_generic_call(): Unit = {
    checkTextHasError(
      s"""
        |def bar[A]() = 0
        |${START}bar$END[Int]
        |""".stripMargin
    )
  }

  def test_generic_call_eta_expand(): Unit = {
    checkTextHasNoErrors(
      s"""
         |def bar[A]() = 0
         |bar[Int]: () => Any
         |""".stripMargin
    )
  }
}
