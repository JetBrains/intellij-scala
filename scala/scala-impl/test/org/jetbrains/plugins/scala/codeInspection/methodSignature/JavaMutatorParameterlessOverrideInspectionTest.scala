package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class JavaMutatorParameterlessOverrideInspectionTest extends ScalaInspectionTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ParameterlessOverrideInspection.JavaMutator]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.parameterless.override.java.mutator")

  private val hint = ScalaInspectionBundle.message("empty.parentheses")


  def test_non_unit_with_mutator_name(): Unit = {
    myFixture.configureByText("JBase.java",
      """
        |public class JBase {
        |    public int addFoo() {}
        |}
      """.stripMargin
    )

    checkTextHasError(
      text =
        s"""
           |class Impl extends JBase {
           |  override def ${START}addFoo$END: Int = 0
           |}
         """.stripMargin
    )

    testQuickFix(
      text =
        s"""
           |class Impl extends JBase {
           |  def add${CARET}Foo: Int = 0
           |}
         """.stripMargin,
      expected =
        s"""
           |class Impl extends JBase {
           |  def addFoo(): Int = 0
           |}
         """.stripMargin,
      hint
    )
  }

  def test_unit_with_non_mutator_name(): Unit = {
    myFixture.configureByText("JBase.java",
      """
        |public class JBase {
        |    public void foo() {}
        |}
      """.stripMargin)

    checkTextHasError(
      text =
        s"""
           |class Impl extends JBase {
           |  def ${START}foo$END: Unit = 0
           |}
         """.stripMargin
    )
  }

  def test_unit_with_mutator_name(): Unit = {
    myFixture.configureByText("JBase.java",
      """
        |public class JBase {
        |    public void addFoo() {}
        |}
      """.stripMargin
    )

    checkTextHasNoErrors(
      text =
        """
          |class Impl extends JBase {
          |  def addFoo(): Unit = ()
          |}
        """.stripMargin
    )
  }

  def test_with_non_mutator_name(): Unit = {
    myFixture.configureByText("JBase.java",
      """
        |public class JBase {
        |    public int foo() { return 0; }
        |}
      """.stripMargin)

    checkTextHasNoErrors(
      """
        |class Impl extends JBase {
        |  def foo: Int = 0
        |}
      """.stripMargin
    )
  }
}
