package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_12, Scala_2_13}

class FunctionLiteralToPartialFunctionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion) = version >= Scala_2_13

  def testPartialFunctionSynthesis(): Unit = checkTextHasNoErrors(
    """
      |val seq = Seq("a", "b")
      |seq.collect(a => a)
      |seq.collect { a => a + "!" }
      |seq.collect { case a => a }
      |
      |def applyPartial[b](f: PartialFunction[Option[String], b])(x: Option[String]) =
      |  if (f.isDefinedAt(x)) f(x) else "<undefined>"
      |
      |applyPartial {
      |  case Some(xxx) => xxx
      |  case None => throw new MatchError(None)
      |} (None);
      |
      |applyPartial(_.get)(None)
      |""".stripMargin)

  def testFunctionLikeResolve(): Unit = checkTextHasNoErrors(
    """
      |def takeFunctionLike(pf: PartialFunction[String, String]) = println("pf wins")
      |takeFunctionLike(_.reverse)
      |takeFunctionLike { case s => s.reverse }
      |""".stripMargin)
}

class FunctionLiteralToPartialFunctionTest2_12 extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_12

  def testCollect(): Unit = checkHasErrorAroundCaret(
    s"""
      |List(1, 2, 3).collect(x => x + 1)
      |(1 to 5).collect { _.toString }
      |""".stripMargin
  )

  def testFunctionLike(): Unit = checkHasErrorAroundCaret(
    s"""
       |def takeFunctionLike(pf: PartialFunction[String, String]) = println("pf wins")
       |takeFunctionLike(_.rev${CARET}erse)
       |takeFunctionLike { case s => s.reverse }
       |""".stripMargin
  )

  def testApplyPartial(): Unit = checkHasErrorAroundCaret(
    s"""
       |def applyPartial[b](f: PartialFunction[Option[String], b])(x: Option[String]) =
       |  if (f.isDefinedAt(x)) f(x) else "<undefined>"
       |
       |applyPartial(_.g${CARET}et)(None)
       |""".stripMargin
  )
}
