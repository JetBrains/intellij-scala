package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}
import org.junit.Assert.assertEquals

class MethodInvocationMatchedTypeParametersTest extends ScalaFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  private def doTest(text: String, expected: Seq[(String, String)]): Unit = {
    val file = myFixture.configureByText(s"${getTestName(false)}.scala", text)

    val actual = file
      .depthFirst()
      .filterByType[MethodInvocation]
      .toSeq
      .sortBy(_.getTextRange.getEndOffset)
      .flatMap(_.matchedTypeParameters)
      .map { case (tpe, typeParameter) =>
        typeParameter.name -> tpe.presentableText(TypePresentationContext.emptyContext, Context.Empty)
      }

    assertEquals(expected, actual)
  }

  def testSimpleInference(): Unit = doTest(
    """class A {
      |  def foo[A](a: A): Unit = ()
      |
      |  foo(1)
      |}
      |""".stripMargin,
    Seq("A" -> "Int")
  )

  def testNamedValueArguments(): Unit = doTest(
    """class A {
      |  def foo[A, B](a: A, b: B): Unit = ()
      |
      |  foo(b = "text", a = 1)
      |}
      |""".stripMargin,
    Seq("A" -> "Int", "B" -> "String")
  )

  def testTypeParameterBounds(): Unit = doTest(
    """trait Base
      |trait Derived extends Base
      |
      |def foo[A >: Base](a: A): A = ???
      |
      |val z: Base = foo(new Derived {})
      |""".stripMargin,
    Seq("A" -> "Base")
  )

  def testInterleavedClauses(): Unit = doTest(
    """class A {
      |  def foo[A](a: A)[B](b: B): Unit = ()
      |
      |  foo(1)("text")
      |}
      |""".stripMargin,
    Seq("A" -> "Int", "B" -> "String")
  )

  def testInterleavedClauseAfterOmittedUsingClause(): Unit = doTest(
    """class A {
      |  given Int = 1
      |
      |  def foo(first: Int)(using Int)[A](second: A): A = second
      |
      |  foo(1)(2)
      |}
      |""".stripMargin,
    Seq("A" -> "Int")
  )
}
