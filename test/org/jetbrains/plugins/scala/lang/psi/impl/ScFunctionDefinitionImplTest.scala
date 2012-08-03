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

  def testReturn() {
    assertRecursionTypeIs("def f(n: Int): Int = return f(n + 1)", TailRecursion)
  }

  def testAndAnd() {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 && f(n)", TailRecursion)
  }

  def testOrOr() {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 || f(n)", TailRecursion)
  }

  def testOtherInfixOperator() {
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 ** f(n)", OrdinaryRecursion)
  }

  private def assertRecursionTypeIs(@Language("Scala") code: String, expectation: RecursionType) {
    val function = code.parse[ScFunctionDefinition]
    assertEquals(expectation, function.recursionType)
  }
}