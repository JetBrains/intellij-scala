package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.psi.util.PsiTreeUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.experimental.categories.Category

/**
 * An expression typed against an explicitly given expected type (see [[org.jetbrains.plugins.scala.lang.psi.types.result.Typeable.type]])
 * passes the expected types derived from it down to its subexpressions, rather than letting them compute
 * their expected types from their contexts.
 */
@Category(Array(classOf[TypecheckerTests]))
class ExplicitExpectedTypeTest extends ScalaLightCodeInsightFixtureTestCase {

  private val Start = "/*start*/"
  private val End   = "/*end*/"

  /**
   * @param expectedType the expected type to type the expression between the markers against,
   *                     or `None` to type it against the expected type computed from its context
   */
  private def doTest(
    @Language("Scala") code: String,
    expectedType:            Option[Option[String]],
    expectedResult:          String
  ): Unit = {
    val file = configureScalaFromFileText(
      s"""object A {
         |  class Inv[T]
         |  def inv[T]: Inv[T] = ???
         |  $code
         |}""".stripMargin
    )
    val text = file.getText

    val expression = PsiTreeUtil.findElementOfClassAtRange(
      file,
      text.indexOf(Start) + Start.length,
      text.indexOf(End),
      classOf[ScExpression]
    )
    assertNotNull("No expression between the markers", expression)

    val typeResult = expectedType match {
      case Some(pt) => expression.`type`(pt.map(ScalaPsiElementFactory.createTypeFromText(_, expression, null).get))
      case None     => expression.`type`()
    }

    val actual = typeResult.getOrAny.presentableText(using TypePresentationContext(expression), Context.Empty)
    assertEquals(expectedResult, actual)
  }

  private def doTestExplicit(@Language("Scala") code: String, expectedType: String, expectedResult: String): Unit =
    doTest(code, Some(Some(expectedType)), expectedResult)

  def testExplicitExpectedTypeOverridesContext(): Unit =
    doTestExplicit("val x: Inv[String] = /*start*/inv/*end*/", "Inv[Int]", "Inv[Int]")

  def testNoExpectedTypeOverridesContext(): Unit =
    doTest("val x: Inv[String] = /*start*/inv/*end*/", Some(None), "Inv[Nothing]")

  def testDefaultIsExpectedTypeFromContext(): Unit =
    doTest("val x: Inv[String] = /*start*/inv/*end*/", None, "Inv[String]")

  def testBlockResult(): Unit =
    doTestExplicit("val x: Inv[String] = /*start*/{ println(); inv }/*end*/", "Inv[Int]", "Inv[Int]")

  def testIfBranches(): Unit =
    doTestExplicit("val x: Inv[String] = /*start*/if (true) inv else inv/*end*/", "Inv[Int]", "Inv[Int]")

  def testMatchBranches(): Unit =
    doTestExplicit("val x: Inv[String] = /*start*/1 match { case _ => inv }/*end*/", "Inv[Int]", "Inv[Int]")

  def testTryAndCatch(): Unit =
    doTestExplicit(
      "val x: Inv[String] = /*start*/try inv catch { case _: Exception => inv }/*end*/",
      "Inv[Int]",
      "Inv[Int]"
    )

  def testParentheses(): Unit =
    doTestExplicit("val x: Inv[String] = /*start*/(inv)/*end*/", "Inv[Int]", "Inv[Int]")

  def testTupleComponents(): Unit =
    doTestExplicit(
      "val x: (Inv[String], Inv[String]) = /*start*/(inv, inv)/*end*/",
      "(Inv[Int], Inv[Long])",
      "(Inv[Int], Inv[Long])"
    )

  def testFunctionLiteralBody(): Unit =
    doTestExplicit("val x: Int => Inv[String] = /*start*/(i: Int) => inv/*end*/", "Int => Inv[Int]", "Int => Inv[Int]")

  def testPatternMatchingAnonymousFunction(): Unit =
    doTestExplicit(
      "val x: Int => Inv[String] = /*start*/{ case i => inv }/*end*/",
      "Int => Inv[Int]",
      "Int => Inv[Int]"
    )

  def testNested(): Unit =
    doTestExplicit(
      "val x: Inv[String] = /*start*/{ if (true) (1 match { case _ => inv }) else { inv } }/*end*/",
      "Inv[Int]",
      "Inv[Int]"
    )
}
