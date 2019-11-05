package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_13}

class FunctionLiteralToPartialFunctionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override implicit val version: ScalaVersion = Scala_2_13

  def testPartialFunctionSynthesis(): Unit = checkTextHasNoErrors(
    """
      |val seq = Seq("a", "b")
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
      |def takeFunctionLike(fn: String => String)                   = println("fn wins")
      |def takeFunctionLike(pf: PartialFunction[String, String])    = println("pf wins")
      |
      |  takeFunctionLike(_.reverse)
      |  takeFunctionLike { case s => s.reverse }
      |  takeFunctionLike { case s: String => s.reverse }
      |""".stripMargin)
}
