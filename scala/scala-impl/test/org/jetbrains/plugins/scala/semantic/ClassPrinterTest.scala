package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ClassPrinterTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testJavaAnnotationArguments(): Unit = {
    myFixture.addClass("package example; public @interface Flag { String issue(); }")
    doTest(
      """package example
        |@Flag(issue = "test")
        |class Test
        |""",
      """package example
        |
        |@example.Flag(issue = "test")
        |class Test
        |"""
    )
  }

  def testTraitSuperclassWithoutArguments(): Unit = doTest(
    """package example
      |class Parent(n: Int)
      |trait Test extends Parent
      |""",
    """package example
      |
      |trait Test extends example.Parent
      |"""
  )

  def testJavaAnnotationPositionalArgumentAndOmittedDefault(): Unit = {
    myFixture.addClass("package example; public @interface Flag { int count() default 1; String value(); }")
    doTest(
      """package example
        |@Flag("test") class Test
        |""",
      """package example
        |
        |@example.Flag("test")
        |class Test
        |"""
    )
  }

  def testJavaAnnotationNamedArgumentsRetainSourceOrder(): Unit = {
    myFixture.addClass("package example; public @interface Flag { String value(); int count() default 1; }")
    doTest(
      """package example
        |@Flag(count = 2, value = "test") class Test
        |""",
      """package example
        |
        |@example.Flag(count = 2, value = "test")
        |class Test
        |"""
    )
  }

  def testJavaAnnotationDefaultsAreNotPrinted(): Unit = {
    myFixture.addClass("package example; public @interface Flag { String value() default \"test\"; }")
    doTest(
      """package example
        |@Flag class Test
        |""",
      """package example
        |
        |@example.Flag
        |class Test
        |"""
    )
  }

  def testSuperclassImplicitArguments(): Unit = doTest(
    """package example
      |class Parent(n: Int)(implicit m: Int)
      |class Test extends Parent(1)(2)
      |""",
    """package example
      |
      |class Test extends example.Parent(1)(using 2)
      |"""
  )

  def testSuperclassInferredImplicitArguments(): Unit = doTest(
    """package example
      |object Defaults {
      |  implicit val n: Int = 2
      |}
      |import Defaults.n
      |class Parent(n: Int)(implicit m: Int)
      |class Test extends Parent(1)
      |""",
    """package example
      |
      |class Test extends example.Parent(1)(using example.Defaults.n)
      |"""
  )

  def testSuperclassUsingArguments(): Unit = doTest(
    """package example
      |class Parent(n: Int)(using m: Int)
      |class Test extends Parent(1)(using 2)
      |""",
    """package example
      |
      |class Test extends example.Parent(1)(using 2)
      |"""
  )

  def testSuperclassLeadingUsingArguments(): Unit = doTest(
    """package example
      |class Parent(using n: Int)
      |class Test extends Parent(using 1)
      |""",
    """package example
      |
      |class Test extends example.Parent(using 1)
      |"""
  )

  def testNewExpressionImplicitArguments(): Unit = doTest(
    """package example
      |class Parent(n: Int)(implicit m: Int)
      |class Test {
      |  def parent: Parent = new Parent(1)(2)
      |}
      |""",
    """package example
      |
      |class Test {
      |  def parent: example.Parent =
      |    new example.Parent(1)(using 2)
      |}
      |"""
  )

  def testNewExpressionDefaultArgumentsWithoutParentheses(): Unit = doTest(
    """package example
      |class Parent(n: Int = 1)
      |class Test {
      |  def parent: Parent = new Parent
      |}
      |""",
    """package example
      |
      |class Test {
      |  def parent: example.Parent =
      |    new example.Parent()
      |}
      |"""
  )

  private def doTest(code: String, expected: String): Unit = {
    val file = configureFromFileText(code.stripMargin)
    val cls = file.depthFirst().filterByType[ScTypeDefinition].find(_.name == "Test").get
    val actual = ClassPrinter.textOf(cls, isScala3 = true).linesIterator.map(_.stripTrailing()).mkString("\n")
    assertEquals(expected.stripMargin.trim, actual.trim)
  }
}
