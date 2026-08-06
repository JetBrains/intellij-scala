package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil

class JavaRecordTest extends TypeInferenceTestBase {
  override protected def setUp(): Unit = {
    super.setUp()
    IdeaTestUtil.setProjectLanguageLevel(this.getProject, LanguageLevel.JDK_17)
  }

  def test_RecordUnification(): Unit = {
    myFixture.configureByText("Expression.java",
      """
        |public interface Expression {
        |    public sealed interface LogicalExpression {
        |        public record A() implements LogicalExpression {
        |        }
        |
        |        public record B() implements LogicalExpression {
        |        }
        |    }
        |}
        |""".stripMargin
    )
    myFixture.allowTreeAccessForAllFiles()

    checkTextHasNoErrors(
      """
        |import Expression.LogicalExpression
        |
        |object Test {
        |  def foo(i: Int): LogicalExpression = {
        |    val x = i match {
        |      case 1 => new LogicalExpression.A()
        |      case 2 => new LogicalExpression.B()
        |    }
        |    x
        |  }
        |}
        |
        |""".stripMargin
    )
  }
}
