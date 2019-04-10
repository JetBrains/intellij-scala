package org.jetbrains.plugins.scala
package lang
package psi
package impl

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.junit.Assert._

/**
  * Pavel Fatin
  */
class ScFunctionDefinitionImplTest extends SimpleTestCase {

  import ScFunctionDefinition.RecursiveReferences

  private val ordinaryRecursion = (_: RecursiveReferences).ordinaryRecursive.nonEmpty

  def testNoRecursion(): Unit =
    assertRecursionTypeIs("def f(n: Int) = n")(_.noRecursion)

  def testLinearRecursion(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = 1 + f(n)")(ordinaryRecursion)

  def testTailRecursion(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = f(n + 1)")()

  def testTailRecursionWithCurring(): Unit =
    assertRecursionTypeIs("def f(n: Int)(x:Int)(y:Int): Int = f(n + 1)(x)(y)")()

  def testTailRecursionWithTypeParam(): Unit =
    assertRecursionTypeIs("def f[A](n: Int): Int = f[A](n + 1)")()

  def testReturn(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = return f(n + 1)")()

  def testAndAnd(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 && f(n)")()

  def testAndAnd2(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) && n > 0")(ordinaryRecursion)

  def testAndAnd3(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) && f(n-1)")(ordinaryRecursion)

  def testOrOr(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 || f(n)")()

  def testOrOr2(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) || n > 0")(ordinaryRecursion)

  def testOrOr3(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = f(n) || f(n-1)")(ordinaryRecursion)

  def testIf(): Unit =
    assertRecursionTypeIs("def f(n: Int) {if (true) {f(n + 1)}}")()

  def testOtherInfixOperator(): Unit =
    assertRecursionTypeIs("def f(n: Int): Boolean = n > 0 ** f(n)")(ordinaryRecursion)

  def testDeeperInfixOperator(): Unit = assertRecursionTypeIs(
    """
      |def f(n: Int): Boolean =
      |  n >=0 && n match {
      |    case 1234 => f(n - 1)
      |    case _ => 1234
      |  }
    """.stripMargin
  )()

  def testRecursionInTry(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = try f(n + 1) catch { case _ => 0 }")(ordinaryRecursion)

  def testRecursionInTryWithReturn(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = try { return f(n + 1); 3 } catch { case _ => 0 }")(ordinaryRecursion)

  def testRecursionInCatch(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = try 0 catch { case _ => f(n + 1) }")()

  def testRecursionInCatchWithReturn(): Unit = assertRecursionTypeIs(
    """
      |def f(n: Int): Int = try 0 catch {
      |  case _ =>
      |    if (n > 3)
      |      return f(n + 1)
      |    f(n + 1)
      |}
    """.stripMargin)()

  def testRecursionInFinally(): Unit =
    assertRecursionTypeIs("def f(n: Int): Int = try 0 finally { return f(n + 1) }")(ordinaryRecursion)

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

    val actual = parseAsFunction(code).returnUsages.map(_.getText)
    assertEquals(Set("return Some(body)", "return None", "f[A](n - 1)(body)"), actual)
  }

  private def assertRecursionTypeIs(@Language("Scala") code: String)
                                   (predicate: RecursiveReferences => Boolean = _.tailRecursionOnly): Unit = {
    val references = parseAsFunction(code).recursiveReferencesGrouped
    assertTrue(predicate(references))
  }

  private def parseAsFunction(@Language("Scala") code: String): ScFunctionDefinition = code.parse[ScFunctionDefinition]
}