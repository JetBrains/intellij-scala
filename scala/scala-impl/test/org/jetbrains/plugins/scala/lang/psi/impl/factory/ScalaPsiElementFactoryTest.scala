package org.jetbrains.plugins.scala.lang.psi.impl.factory

import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.util.NotNothing
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.{assertNotNull, assertTrue}

import scala.reflect.{ClassTag, classTag}

abstract class ScalaPsiElementFactoryTestBase extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_2_13

  protected implicit def features: ScalaFeatures

  protected def checkCreateExprFromText[T <: ScExpression : ClassTag : NotNothing](text: String): Unit =
    doCheckCreateExprFromText(text) { expr =>
      val errorMessage =
        s"Expected ${classTag[T].runtimeClass.getCanonicalName}, got: ${expr.getClass.getCanonicalName}"
      assertTrue(errorMessage, expr.is[T])
    }

  def testStringLiteral(): Unit = checkCreateExprFromText[ScStringLiteral](""""This is a regular string."""")

  def testMultiLineStringLiteral(): Unit = checkCreateExprFromText[ScStringLiteral](
    s"""""\"This is a
       |multi-line
       |string.""\"""".stripMargin
  )

  def testStringLiteralWithInterpolation(): Unit =
    checkCreateExprFromText[ScInterpolatedStringLiteral](s"""s"Hello, $$name!"""")

  def testBooleanLiteral(): Unit = checkCreateExprFromText[ScBooleanLiteral]("true")

  def testCharLiteral(): Unit = checkCreateExprFromText[ScCharLiteral]("'c'")

  def testDoubleLiteral(): Unit = checkCreateExprFromText[ScDoubleLiteral]("1d")

  def testFloatLiteral(): Unit = checkCreateExprFromText[ScFloatLiteral]("2f")

  def testIntLiteral(): Unit = checkCreateExprFromText[ScIntegerLiteral]("3")

  def testLongLiteral(): Unit = checkCreateExprFromText[ScLongLiteral]("4L")

  def testNullLiteral(): Unit = checkCreateExprFromText[ScNullLiteral]("null")

  def testSymbolLiteral(): Unit = checkCreateExprFromText[ScSymbolLiteral]("'s")

  def testAssignment(): Unit = checkCreateExprFromText[ScAssignment]("x = 2")

  def testAssignment2(): Unit = checkCreateExprFromText[ScAssignment](
    """x =
      |  2""".stripMargin
  )

  def testBlockExpr(): Unit = checkCreateExprFromText[ScBlockExpr]("{}")

  def testBlockExpr2(): Unit = checkCreateExprFromText[ScBlockExpr](
    """{
      |  println(1)
      |  println(2)
      |}""".stripMargin
  )

  def testDoWhile(): Unit = checkCreateExprFromText[ScDo](
    """do {
      |  i += 1
      |} while (f(i) == 0)""".stripMargin
  )

  def testFor(): Unit = checkCreateExprFromText[ScFor]("for (i <- ints) println(i)")

  def testForMultiline(): Unit = checkCreateExprFromText[ScFor](
    """for (i <- ints) {
      |  val x = i * 2
      |  println(s"i = $i, x = $x")
      |}""".stripMargin
  )

  def testForMultilineGenerators(): Unit = checkCreateExprFromText[ScFor](
    """for {
      |  i <- 1 to 2
      |  j <- 'a' to 'b'
      |  k <- 1 to 10 by 5
      |} {
      |  println(s"i = $i, j = $j, k = $k")
      |}""".stripMargin
  )

  def testForExpr(): Unit = checkCreateExprFromText[ScFor](
    """for (i <- 10 to 12)
      |  yield i * 2""".stripMargin
  )

  def testWhile(): Unit = checkCreateExprFromText[ScWhile](
    """while (i < 3) {
      |  println(i)
      |  i += 1
      |}""".stripMargin
  )

  def testIf(): Unit = checkCreateExprFromText[ScIf]("if (a < b) a else b")

  def testIfMultiline(): Unit = checkCreateExprFromText[ScIf](
    """if (x == 1) {
      |  println("x is 1, as you can see:")
      |  println(x)
      |} else {
      |  println("x was not 1")
      |}""".stripMargin
  )

  def testIfMultilineExtraNewLines(): Unit = checkCreateExprFromText[ScIf](
    """
      |if (true)
      |  return true
      |else {
      |  return false
      |}
      |""".stripMargin
  )

  def testMatch(): Unit = checkCreateExprFromText[ScMatch](
    """i match {
      |  case 0 => println("1")
      |  case 1 => println("2")
      |  case what => println(s"You gave me: $what")
      |}""".stripMargin
  )

  def testTry(): Unit = checkCreateExprFromText[ScTry](
    """try {
      |  text = openAndReadAFile(filename)
      |} catch {
      |  case fnf: FileNotFoundException => fnf.printStackTrace()
      |  case ioe: IOException => ioe.printStackTrace()
      |} finally {
      |  // close your resources here
      |  println("Came to the 'finally' clause.")
      |}""".stripMargin
  )

  def testNew(): Unit = checkCreateExprFromText[ScNewTemplateDefinition]("new Foo")

  def testNewMultiline(): Unit = checkCreateExprFromText[ScNewTemplateDefinition](
    """new Foo {
      |  val bar = 1
      |}""".stripMargin
  )

  def testGenericCall(): Unit = checkCreateExprFromText[ScGenericCall]("foo[Bar]")

  def testMethodCall(): Unit = checkCreateExprFromText[ScMethodCall]("foo(1)")

  def testMethodCall2(): Unit = checkCreateExprFromText[ScMethodCall]("foo(x => x.bar)")

  def testMethodCallMultiline(): Unit = checkCreateExprFromText[ScMethodCall](
    """foo(1) {
      |  bar
      |}""".stripMargin
  )

  def testInfixExpr(): Unit = checkCreateExprFromText[ScInfixExpr]("(a + b) <= (c - d)")

  def testInfixExprMultiline(): Unit = checkCreateExprFromText[ScInfixExpr](
    """list map {
      |  x => x + 2
      |}""".stripMargin
  )

  def testPostfixExpr(): Unit = checkCreateExprFromText[ScPostfixExpr]("foo bar")

  def testPrefixExpr(): Unit = checkCreateExprFromText[ScPrefixExpr]("!foo")

  def testParenthesizedExpr(): Unit = checkCreateExprFromText[ScParenthesisedExpr]("(foo)")

  def testParenthesizedExprMultiline(): Unit = checkCreateExprFromText[ScParenthesisedExpr](
    """(
      |  2
      |)""".stripMargin
  )

  def testReferenceExpr(): Unit = checkCreateExprFromText[ScReferenceExpression]("foo")

  def testReturnExpr(): Unit = checkCreateExprFromText[ScReturn]("return 2")

  def testThrow(): Unit = checkCreateExprFromText[ScThrow]("throw Foo")

  def testThrowMultiline(): Unit = checkCreateExprFromText[ScThrow](
    """throw
      |  Foo""".stripMargin
  )

  def testTuple(): Unit = checkCreateExprFromText[ScTuple]("""(1, "s", false)""")

  def testTupleMultiline(): Unit = checkCreateExprFromText[ScTuple](
    """(1,
      |  "s",
      |  false)""".stripMargin
  )

  def testTypedExpr(): Unit = checkCreateExprFromText[ScTypedExpression]("x: Foo")

  def testUnderscoreSection(): Unit = checkCreateExprFromText[ScUnderscoreSection]("_")

  def testUnitExpr(): Unit = checkCreateExprFromText[ScUnitExpr]("()")

  def testFunctionExpr(): Unit = checkCreateExprFromText[ScFunctionExpr]("t => x")

  def testFunctionExprMultiline(): Unit = checkCreateExprFromText[ScFunctionExpr](
    """t =>
      |  println(t)
      |  x""".stripMargin
  )

  private def doCheckCreateExprFromText(text: String)(extraValidation: ScExpression => Unit): Unit = {
    val expr = ScalaPsiElementFactory.createExpressionFromText(text, features)(getProject)
    assertNotNull(expr)

    val containingFile = expr.getContainingFile
    assertNotNull(containingFile)

    val errors = containingFile.breadthFirst().filter(_.is[PsiErrorElement]).toSeq
    val errorMessage =
      s"""Encountered parsing errors:
         |  - ${errors.mkString("\n  - ")}""".stripMargin
    assertTrue(errorMessage, errors.isEmpty)

    extraValidation(expr)
  }

}

final class ScalaPsiElementFactoryTest_Scala_2_12 extends ScalaPsiElementFactoryTestBase {
  override protected implicit val features: ScalaFeatures = ScalaFeatures.onlyByVersion(LatestScalaVersions.Scala_2_12)
}

final class ScalaPsiElementFactoryTest_Scala_2_13 extends ScalaPsiElementFactoryTestBase {
  override protected implicit val features: ScalaFeatures = ScalaFeatures.onlyByVersion(LatestScalaVersions.Scala_2_13)
}

final class ScalaPsiElementFactoryTest_Scala_3 extends ScalaPsiElementFactoryTestBase {
  override protected implicit val features: ScalaFeatures = ScalaFeatures.onlyByVersion(LatestScalaVersions.Scala_3_3)

  def testQuotedBlock(): Unit = checkCreateExprFromText[ScQuotedBlock]("'{ x }")

  def testSplicedBlock(): Unit = checkCreateExprFromText[ScSplicedBlock]("${ x }")

  def testPolyFunctionExpr(): Unit = checkCreateExprFromText[ScPolyFunctionExpr]("[t] => t => F")

  def testPolyFunctionExprMultiline(): Unit = checkCreateExprFromText[ScPolyFunctionExpr](
    """[t] =>
      |  t =>
      |  F""".stripMargin
  )

  def testFor_NewSyntax(): Unit = checkCreateExprFromText[ScFor]("for i <- ints do println(i)")

  def testForMultiline_IndentationBased(): Unit = checkCreateExprFromText[ScFor](
    """for i <- ints
      |do
      |  val x = i * 2
      |  println(s"i = $i, x = $x")""".stripMargin
  )

  def testForMultilineGenerators_IndentationBased(): Unit = checkCreateExprFromText[ScFor](
    """for
      |  i <- 1 to 2
      |  j <- 'a' to 'b'
      |  k <- 1 to 10 by 5
      |do
      |  println(s"i = $i, j = $j, k = $k")""".stripMargin
  )

  def testForExpr_NewSyntax(): Unit = checkCreateExprFromText[ScFor](
    """for i <- 10 to 12
      |  yield i * 2""".stripMargin
  )

  def testWhile_IndentationBased(): Unit = checkCreateExprFromText[ScWhile](
    """while i < 3 do
      |  println(i)
      |  i += 1""".stripMargin
  )

  def testIf_NewSyntax(): Unit = checkCreateExprFromText[ScIf]("if a < b then a else b")

  def testIfMultiline_NewSyntax(): Unit = checkCreateExprFromText[ScIf](
    """if x == 1 then
      |  println("x is 1, as you can see:")
      |  println(x)
      |else
      |  println("x was not 1")""".stripMargin
  )

  def testIfMultilineExtraNewLines_NewSyntax(): Unit = checkCreateExprFromText[ScIf](
    """
      |if true then
      |  return true
      |else
      |  return false
      |""".stripMargin
  )

  def testMatch_IndentationBased(): Unit = checkCreateExprFromText[ScMatch](
    """i match
      |  case 0 => println("1")
      |  case 1 => println("2")
      |  case what => println(s"You gave me: $what")""".stripMargin
  )

  def testTry_IndentationBased(): Unit = checkCreateExprFromText[ScTry](
    """try
      |  text = openAndReadAFile(filename)
      |catch
      |  case fnf: FileNotFoundException => fnf.printStackTrace()
      |  case ioe: IOException => ioe.printStackTrace()
      |finally
      |  // close your resources here
      |  println("Came to the 'finally' clause.")""".stripMargin
  )

  def testNewMultiline_IndentationBased(): Unit = checkCreateExprFromText[ScNewTemplateDefinition](
    """new Foo:
      |  val bar = 1""".stripMargin
  )

  def testMethodCallMultiline_FewerBraces(): Unit = checkCreateExprFromText[ScMethodCall](
    """foo(1):
      |  bar""".stripMargin
  )

  def testInfixExprMultiline_FewerBraces(): Unit = checkCreateExprFromText[ScInfixExpr](
    """list map:
      |  x => x + 2""".stripMargin
  )
}
