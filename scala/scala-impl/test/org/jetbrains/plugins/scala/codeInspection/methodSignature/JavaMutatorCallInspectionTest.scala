package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}

class JavaMutatorCallInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ParameterlessAccessInspection.JavaMutator]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.parameterless.access.java.mutator")

  private val hint = ScalaInspectionBundle.message("add.call.parentheses")


  def test_non_unit_with_mutator_name(): Unit = {
    getFixture.configureByText("J.java",
      """
        |public class J {
        |    public int addFoo() {}
        |}
      """.stripMargin
    )

    checkTextHasError(
      text =
        s"""
           |new J().${START}addFoo$END
         """.stripMargin
    )

    testQuickFix(
      text =
        s"""
           |new J().add${CARET}Foo
         """.stripMargin,
      expected =
        s"""
           |new J().addFoo()
         """.stripMargin,
      hint
    )
  }

  def test_unit_with_non_mutator_name(): Unit = {
    getFixture.configureByText("J.java",
      """
        |public class J {
        |    public void foo() {}
        |}
      """.stripMargin)

    checkTextHasError(
      text =
        s"""
           |new J().${START}foo$END
         """.stripMargin
    )
  }

  def test_unit_with_mutator_name(): Unit = {
    getFixture.configureByText("J.java",
      """
        |public class J {
        |    public void addFoo() {}
        |}
      """.stripMargin
    )

    checkTextHasNoErrors(
      text =
        """
          |new J().addFoo()
        """.stripMargin
    )
  }

  def test_with_non_mutator_name(): Unit = {
    getFixture.configureByText("J.java",
      """
        |public class J {
        |    public int foo() { return 0; }
        |}
      """.stripMargin)

    checkTextHasNoErrors(
      """
        |new J().foo
      """.stripMargin
    )
  }
}
