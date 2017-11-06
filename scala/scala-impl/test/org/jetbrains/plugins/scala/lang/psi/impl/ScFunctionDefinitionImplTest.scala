package org.jetbrains.plugins.scala
package lang.psi.impl

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.RecursionType.{NoRecursion, OrdinaryRecursion, TailRecursion}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{RecursionType, ScFunctionDefinition}
import org.junit.Assert._

/**
  * Pavel Fatin
  */
class ScFunctionDefinitionImplTest extends SimpleTestCase {

  def testNoRecursion(): Unit = {
    assertRecursionTypeIs("def f(n: Int) = n", NoRecursion)
  }

  def testLinearRecursion(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Int = 1 + f(n)", OrdinaryRecursion)
  }

  def testTailRecursion(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Int = f(n + 1)", TailRecursion)
  }

  def testTailRecursionWithCurring(): Unit = {
    assertRecursionTypeIs("def f(n: Int)(x:Int)(y:Int): Int = f(n + 1)(x)(y)", TailRecursion)
  }

  def testTailRecursionWithTypeParam(): Unit = {
    assertRecursionTypeIs("def f[A](n: Int): Int = f[A](n + 1)", TailRecursion)
  }

  def testReturn(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Int = return f(n + 1)", TailRecursion)
  }

  def testAndAnd(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 && f(n)", TailRecursion)
  }

  def testAndAnd2(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) && n > 0", OrdinaryRecursion)
  }

  def testAndAnd3(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) && f(n-1)", OrdinaryRecursion)
  }

  def testOrOr(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 || f(n)", TailRecursion)
  }

  def testOrOr2(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) || n > 0", OrdinaryRecursion)
  }

  def testOrOr3(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) || f(n-1)", OrdinaryRecursion)
  }

  def testIf(): Unit = {
    assertRecursionTypeIs("def f(n: Int) {if (true) {f(n + 1)}}", TailRecursion)
  }

  def testOtherInfixOperator(): Unit = {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 ** f(n)", OrdinaryRecursion)
  }

  def testDeeperInfixOperator(): Unit = {
    assertRecursionTypeIs(
      """
        |def f(n: Int): Boolean =
        |  n >=0 && n match {
        |    case 1234 => f(n - 1)
        |    case _ => 1234
        |  }
      """.stripMargin, TailRecursion)
  }

  def testGetReturnUsages(): Unit = {
    val code =
      """
        |def f[A](n: Int)(body: => A): Option[A] = {
        |  try
        |    return Some(body)
        |  catch {
        |    case e: Exception if n == 0 => return None
        |  }
        |  f[A](n - 1)(body)
        |}
      """.stripMargin

    val actualUsages = parseAsFunction(code).returnUsages
    assertEquals(Set("return Some(body)", "return None", "f[A](n - 1)(body)"), actualUsages.map(_.getText))
  }

  private def assertRecursionTypeIs(@Language("Scala") code: String, expected: RecursionType): Unit = {
    assertEquals(expected, parseAsFunction(code).recursionType)
  }

  private def parseAsFunction(@Language("Scala") code: String): ScFunctionDefinition = code.parse[ScFunctionDefinition]
}