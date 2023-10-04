package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.junit.Assert.assertTrue

class MethodInvocationTest extends ScalaFixtureTestCase {

  private def assertParameterType(parameter: Parameter, expectedType: ScType): Unit = {

    val actualType = parameter.paramType

    assertTrue(
      s"Expected $expectedType but got $actualType instead",
      actualType == expectedType
    )
  }

  def test_method_invocation_matched_parameters_order(): Unit = {

    val text =
      """class A {
        |  def doubleFloatIntLongToUnit(d: Double, f: Float, i: Int, l: Long): Unit = {}
        |  doubleFloatIntLongToUnit(1.0, 2.0f, 3, 4L)
        |}
        |""".stripMargin

    val parameters =
      myFixture.configureByText(s"${getTestName(false)}.scala", text)
        .depthFirst().findByType[MethodInvocation].toSeq.flatMap(_.matchedParameters)

    assertTrue(parameters.size == 4)

    assertParameterType(parameters.head._2, StdTypes.instance.Double)
    assertParameterType(parameters(1)._2, StdTypes.instance.Float)
    assertParameterType(parameters(2)._2, StdTypes.instance.Int)
    assertParameterType(parameters(3)._2, StdTypes.instance.Long)
  }

  def test_method_invocation_matched_parameters_named_arguments_order(): Unit = {

    val text =
      """class A {
        |  def doubleFloatIntLongToUnit(d: Double, f: Float, i: Int, l: Long): Unit = {}
        |  doubleFloatIntLongToUnit(l = 4L, d = 1.0, f = 2.0f, i = 3)
        |}
        |""".stripMargin

    myFixture.configureByText(s"${getTestName(false)}.scala", text)

    val parameters =
      myFixture.configureByText(s"${getTestName(false)}.scala", text)
        .depthFirst().findByType[MethodInvocation].toSeq.flatMap(_.matchedParameters)

    assertTrue(parameters.size == 4)

    assertParameterType(parameters.head._2, StdTypes.instance.Long)
    assertParameterType(parameters(1)._2, StdTypes.instance.Double)
    assertParameterType(parameters(2)._2, StdTypes.instance.Float)
    assertParameterType(parameters(3)._2, StdTypes.instance.Int)
  }
}
