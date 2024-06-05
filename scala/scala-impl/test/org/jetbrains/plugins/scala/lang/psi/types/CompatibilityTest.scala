package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.junit.Assert.assertTrue

class CompatibilityTest extends ScalaFixtureTestCase {

  private class DummyExpression(`type`: ScType) extends Expression {
    override def getTypeAfterImplicitConversion(
      checkImplicits: Boolean,
      isShape: Boolean,
      expectedOption: Option[ScType],
      ignoreBaseTypes: Boolean,
      fromUnderscore: Boolean): ScExpression.ExpressionTypeResult = ExpressionTypeResult(Right(`type`))
  }

  def test_checkConformanceExt_matched_parameters_order(): Unit = {

    val p1 = Parameter("", None, StdTypes.instance.Double, StdTypes.instance.Double)
    val p2 = Parameter("", None, StdTypes.instance.Float, StdTypes.instance.Float)
    val p3 = Parameter("", None, StdTypes.instance.Int, StdTypes.instance.Int)
    val p4 = Parameter("", None, StdTypes.instance.Long, StdTypes.instance.Long)

    val e1 = new DummyExpression(StdTypes.instance.Double)
    val e2 = new DummyExpression(StdTypes.instance.Float)
    val e3 = new DummyExpression(StdTypes.instance.Int)
    val e4 = new DummyExpression(StdTypes.instance.Long)

    val parameters: Seq[Parameter] = Seq(p1, p2, p3, p4)
    val expressions: Seq[DummyExpression] = Seq(e1, e2, e3, e4)

    val conformanceExtResult =
      Compatibility.checkMethodApplicability(parameters, expressions, withImplicits = false, isOverloaded = false)

    val matchedParameters: Seq[Parameter] = conformanceExtResult.matched.map(_._1)

    assertTrue(parameters == matchedParameters)
  }
}
