package org.jetbrains.plugins.scala
package lang.psi.impl

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.intellij.lang.annotations.Language
import org.junit.Assert._
import org.jetbrains.plugins.scala.lang.psi.api.statements.RecursionType.{OrdinaryRecursion, TailRecursion, NoRecursion}
import lang.psi.api.statements.{ScFunctionDefinition, RecursionType}

/**
 * Pavel Fatin
 */
class ScFunctionDefinitionImplTest extends SimpleTestCase {
  def testNoRecursion() {
    assertRecursionTypeIs("def f(n: Int) = n", NoRecursion)
  }

  def testLinearRecursion() {
    assertRecursionTypeIs("def f(n: Int): Int = 1 + f(n)", OrdinaryRecursion)
  }

  def testTailRecursion() {
    assertRecursionTypeIs("def f(n: Int): Int = f(n + 1)", TailRecursion)
  }

  def testTailRecursionWithCurring() {
    assertRecursionTypeIs("def f(n: Int)(x:Int)(y:Int): Int = f(n + 1)(x)(y)", TailRecursion)
  }

  def testTailRecursionWithTypeParam() {
    assertRecursionTypeIs("def f[A](n: Int): Int = f[A](n + 1)", TailRecursion)
  }

  def testReturn() {
    assertRecursionTypeIs("def f(n: Int): Int = return f(n + 1)", TailRecursion)
  }

  def testAndAnd() {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 && f(n)", TailRecursion)
  }

  def testAndAnd2() {
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) && n > 0", OrdinaryRecursion)
  }

  def testAndAnd3() {
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) && f(n-1)", OrdinaryRecursion)
  }

  def testOrOr() {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 || f(n)", TailRecursion)
  }

  def testOrOr2() {
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) || n > 0", OrdinaryRecursion)
  }

  def testOrOr3() {
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) || f(n-1)", OrdinaryRecursion)
  }

  def testIf() {
    assertRecursionTypeIs("def f(n: Int) {if (true) {f(n + 1)}}", TailRecursion)
  }

  def testOtherInfixOperator() {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 ** f(n)", OrdinaryRecursion)
  }

  def testGetReturnUsages() {
    assertUsages(
      """
        def f[A](n: Int)(body: => A): Option[A] = {
          try
            return Some(body)
          catch {
            case e: Exception if n == 0 => return None
          }
          f[A](n - 1)(body)
        }
      """,
      "return Some(body)",
      "return None",
      "f[A](n - 1)(body)")
  }


  private def assertUsages(@Language("Scala") code: String, expected: String*) {
    assertEquals(expected, parse(code).getReturnUsages.map(_.getText).toSeq)
  }

  private def assertRecursionTypeIs(@Language("Scala") code: String, expectation: RecursionType) {
    assertEquals(expectation, parse(code).recursionType)
  }

  private def parse(@Language("Scala") code: String): ScFunctionDefinition = code.parse[ScFunctionDefinition]
}