package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class JavaAccessorMethodOverriddenAsEmptyParenInspectionTest extends ScalaInspectionTestBase {
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[EmptyParenOverrideInspection.JavaAccessorMethodOverriddenAsEmptyParenInspection]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.empty.paren.override.java.accessor")

  private val hint = ScalaInspectionBundle.message("redundant.parentheses")


  def test_non_unit_with_accessor_name(): Unit = {
    myFixture.configureByText("JBase.java",
      """
        |public class JBase {
        |    public int getFoo() {}
        |}
      """.stripMargin
    )

    checkTextHasError(
      text =
        s"""
           |class Impl extends JBase {
           |  override def ${START}getFoo$END(): Int = 0
           |}
         """.stripMargin
    )

    testQuickFix(
      text =
        s"""
           |class Impl extends JBase {
           |  def get${CARET}Foo(): Int = 0
           |}
         """.stripMargin,
      expected =
        s"""
           |class Impl extends JBase {
           |  def getFoo: Int = 0
           |}
         """.stripMargin,
      hint
    )
  }

  def test_non_unit_with_non_accessor_name(): Unit = {
    myFixture.configureByText("JBase.java",
      """
        |public class JBase {
        |    public int foo() { return 0; }
        |}
      """.stripMargin)

    checkTextHasNoErrors(
      text =
        s"""
           |class Impl extends JBase {
           |  def foo(): Int = 0
           |}
         """.stripMargin
    )
  }

  def test_unit_with_accessor_name(): Unit = {
    myFixture.configureByText("JBase.java",
      """
        |public class JBase {
        |    public void getFoo() {}
        |}
      """.stripMargin
    )

    checkTextHasNoErrors(
      text =
        """
          |class Impl extends JBase {
          |  def getFoo(): Unit = ()
          |}
        """.stripMargin
    )
  }

  def test_unit_with_non_accessor_name(): Unit = {
    myFixture.configureByText("JBase.java",
      """
        |public class JBase {
        |    public void foo() { }
        |}
      """.stripMargin)

    checkTextHasNoErrors(
      """
        |class Impl extends JBase {
        |  def foo(): Unit = ()
        |}
      """.stripMargin
    )
  }
}
