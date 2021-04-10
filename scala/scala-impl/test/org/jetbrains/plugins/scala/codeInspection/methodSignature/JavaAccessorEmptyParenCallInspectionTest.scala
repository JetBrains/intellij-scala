package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}

class JavaAccessorEmptyParenCallInspectionTest extends ScalaQuickFixTestBase {
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[JavaAccessorEmptyParenCallInspection]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.java.accessor.empty.paren")

  private val hint = ScalaInspectionBundle.message("remove.call.parentheses")


  def test_non_unit_with_accessor_name(): Unit = {
    getFixture.configureByText("J.java",
      """
        |public class J {
        |    public int getFoo() {}
        |}
      """.stripMargin
    )

    checkTextHasError(
      text =
        s"""
           |new J().${START}getFoo$END()
         """.stripMargin
    )

    testQuickFix(
      text =
        s"""
           |new J().get${CARET}Foo()
         """.stripMargin,
      expected =
        s"""
           |new J().getFoo
         """.stripMargin,
      hint
    )
  }

  def test_unit_with_accessor_name(): Unit = {
    getFixture.configureByText("J.java",
      """
        |public class J {
        |    public void getFoo() {}
        |}
      """.stripMargin)

    checkTextHasNoErrors(
      text =
        s"""
           |new J().getFoo()
         """.stripMargin
    )
  }

  def test_with_overloaded_accessor_name(): Unit = {
    getFixture.configureByText("J.java",
      """
        |public class J {
        |    public int getFoo() {}
        |    public int getFoo(int param) {}
        |}
      """.stripMargin)

    checkTextHasNoErrors(
      text =
        s"""
           |new J().getFoo()
         """.stripMargin
    )
  }

  def test_with_non_accessor_name(): Unit = {
    getFixture.configureByText("J.java",
      """
        |public class J {
        |    public int foo() {}
        |}
      """.stripMargin
    )

    checkTextHasNoErrors(
      text =
        """
          |new J().foo()
        """.stripMargin
    )
  }

  def test_bean_properties(): Unit = {
    checkTextHasNoErrors(
      text =
        """
          |object Test {
          |  @beans.BeanProperty
          |  val foo = ""
          |}
          |
          |Test.getFoo()
        """.stripMargin
    )
  }

  def test_scala_get_method(): Unit = {
    checkTextHasNoErrors(
      text =
        """
          |object Test {
          |  val getFoo() = ""
          |  val getBar() = ""
          |}
          |
          |Test.getFoo
          |Test.getFoo()
          |Test.getBar
          |Test.getBar()
        """.stripMargin
    )
  }
}
