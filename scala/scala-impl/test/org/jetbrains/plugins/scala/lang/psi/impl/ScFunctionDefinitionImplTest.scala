package org.jetbrains.plugins.scala
package lang
package psi
package impl

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.{RecursiveReferences, ScExpressionExt, ScFunctionDefinition}
import org.junit.Assert._

class ScFunctionDefinitionImplTest extends SimpleTestCase {

  private val tailRecursion = (_: RecursiveReferences).tailRecursionOnly
  private val ordinaryRecursion = (_: RecursiveReferences).ordinaryRecursive.nonEmpty

  def testNoRecursion(): Unit =
    assertRecursionTypeIs("def f(n: Int) = n")(_.noRecursion)

  def testLinearRecursion(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = 1 + f(n)")(ordinaryRecursion)

  def testTailRecursion(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = f(n + 1)")(tailRecursion)

  def testTailRecursionWithCurring(): Unit =
    assertRecursionTypeIs("def f(n: Int)(x:Int)(y:Int): Int = f(n + 1)(x)(y)")(tailRecursion)

  def testTailRecursionWithTypeParam(): Unit =
    assertRecursionTypeIs("def f[A](n: Int): Int = f[A](n + 1)")(tailRecursion)

  def testReturn(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = return f(n + 1)")(tailRecursion)

  def testAndAnd(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 && f(n)")(tailRecursion)

  def testAndAnd2(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) && n > 0")(ordinaryRecursion)

  def testAndAnd3(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) && f(n-1)")(ordinaryRecursion)

  def testOrOr(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 || f(n)")(tailRecursion)

  def testOrOr2(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) || n > 0")(ordinaryRecursion)

  def testOrOr3(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) || f(n-1)")(ordinaryRecursion)

  def testIfElse(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = { if (true) f(n + 1) else false }")(tailRecursion)

  def testIfWithoutElse(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = { if (true) f(n + 1); false }")(ordinaryRecursion)

  def testIfWithoutElseUnitReturnType(): Unit =
    assertRecursionTypeIs("def f(n: Int): Unit = { if (true) f(n + 1) }")(tailRecursion)

  def testIfWithoutElseUnitReturnTypeImperative(): Unit =
    assertRecursionTypeIs("def f(n: Int) { if (true) f(n + 1) }")(tailRecursion)

  def testOtherInfixOperator(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 ** f(n)")(ordinaryRecursion)

  def testDeeperInfixOperator(): Unit = assertRecursionTypeIs(
    """def f(n: Int): Boolean =
      |  n >=0 && n match {
      |    case 1234 => f(n - 1)
      |    case _ => 1234
      |  }
    """.stripMargin
  )(tailRecursion)

  def testRecursionInTry(): Unit =
    assertRecursionTypeIs(
      """def f(n: Int): Int =
        |  try f(n + 1)
        |  catch { case _ => 0 }
        |""".stripMargin)(ordinaryRecursion)

  def testRecursionInTryWithReturn(): Unit =
    assertRecursionTypeIs(
      """def f(n: Int): Int =
        |  try { return f(n + 1); 3 }
        |  catch { case _ => 0 }
        |""".stripMargin)(ordinaryRecursion)

  def testRecursionInCatch(): Unit =
    assertRecursionTypeIs(
      """def f(n: Int): Int = try 0 catch { case _ =>
        |  f(n + 1)
        |}
        |""".stripMargin)(tailRecursion)

  def testRecursionInCatchWithIfElseAndReturn(): Unit = assertRecursionTypeIs(
    """def f(n: Int): Int = try 0 catch {
      |  case _ =>
      |    if (n > 3) return f(n + 1)
      |    else f(n + 1)
      |}
    """.stripMargin)(tailRecursion)

  def testRecursionInCatchhWithIfAndReturn(): Unit = assertRecursionTypeIs(
    """def f(n: Int): Int = try 0 catch {
      |  case _ =>
      |    if (n > 3) return f(n + 1)
      |    f(n + 1)
      |}
    """.stripMargin)(ordinaryRecursion)

  def testRecursionInCatchWithFinallyBlock(): Unit = assertRecursionTypeIs(
    """def f(n: Int): Int = try 0 catch {
      |  case _ => f(n + 1)
      |} finally {
      |}
    """.stripMargin)(ordinaryRecursion)

  def testRecursionInFinally(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = try 0 finally { return f(n + 1) }")(ordinaryRecursion)

  def testReturnWithIfElse(): Unit =
    assertRecursionTypeIs(
      """def foo(n: Int): Boolean = {
        |  if(n < 0) foo(n - 1)
        |  else if(n == 42) return foo(n - 2)
        |  else foo(n - 3)
        |}
      """.stripMargin)(tailRecursion)

  def testReturnWithIfWithoutTailElse(): Unit =
    assertRecursionTypeIs(
      """def foo(n: Int): Boolean = {
        |  if(n < 0) return foo(n - 1)
        |  else if(n == 42) return foo(n - 2)
        |  foo(n - 3)
        |}
      """.stripMargin)(ordinaryRecursion)

  def testReturnWithPatternMatching(): Unit =
    assertRecursionTypeIs(
      """def bar(x: Int): Boolean = {
        |  x match {
        |    case 42 => return bar(x - 1)
        |    case 23 => bar(x - 2)
        |  }
        |}
      """.stripMargin)(tailRecursion)

  def testRecursionInsideMatch(): Unit =
    assertRecursionTypeIs(
      """def fail2[T](xs: List[T]): List[T] = xs match {
        |  case Nil      => Nil
        |  case x :: xs  => x :: fail2[T](xs)
        |}
      """.stripMargin)(ordinaryRecursion)

  def testGetReturnUsages(): Unit = {
    val code =
      """def f[A](n: Int)(body: => A): Option[A] = {
        |  try
        |    return Some(body)
        |  catch {
        |    case e: Exception if n == 0 => return None
        |  }
        |  f[A](n - 1)(body)
        |}
      """.stripMargin

    val actual = parseAsFunction(code).returnUsages.map(_.getText)
    assertEquals(Set(
      "return Some(body)",
      "return None",
      "f[A](n - 1)(body)"
    ), actual)
  }

  def testCalculateTailReturns(): Unit ={
    val code =
      """def foo(x: Int): Int = {
        |  if (x == 42) return 1
        |  if (x == 23) return 2
        |  else 3
        |}
      """.stripMargin

    val actual = parseAsFunction(code).body.get.calculateTailReturns.map(_.getText)
    assertEquals(Set(
      "return 2",
      "3"
    ), actual)
  }

  private def assertRecursionTypeIs(@Language("Scala") code: String)
                                   (predicate: RecursiveReferences => Boolean): Unit = {
    val references = parseAsFunction(code).recursiveReferencesGrouped
    assertTrue(predicate(references))
  }

  private def parseAsFunction(@Language("Scala") code: String): ScFunctionDefinition =
    code.parse[ScFunctionDefinition]
}