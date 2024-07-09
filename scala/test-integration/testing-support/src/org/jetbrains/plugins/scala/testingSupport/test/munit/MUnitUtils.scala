package org.jetbrains.plugins.scala.testingSupport.test.munit

import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}

private[testingSupport]
object MUnitUtils {

  /** see munit.BaseFunSuite.test & see munit.FunSuite.property */
  val FunSuiteTestMethodNames: Set[String] = Set("test", "property")

  // Base trait containing the test method definition in MUnit 1.0+
  val BaseFunSuiteFqn = "munit.BaseFunSuite"
  // Base class containing the test method definition in MUnit 0.x
  val FunSuiteFqn = "munit.FunSuite"
  val ScalaCheckSuiteFqn = "munit.ScalaCheckSuite"

  val FunSuiteFqnList: List[String] = List(BaseFunSuiteFqn, FunSuiteFqn)
  val ScalaCheckSuiteFqnList: List[String] = List(ScalaCheckSuiteFqn)

  /**
   * @param testRef element representing `test` node in FunSuite test definition:<br>
   *                '''test'''("my test 1") { ... }
   */
  def hasStaticTestName(testRef: ScReferenceExpression): Boolean =
    testNameElement(testRef).exists(isStaticTestNameElement)

  def staticTestName(testRef: ScReferenceExpression): Option[String] =
    testNameElement(testRef).flatMap(staticTestNameOfParameter)

  private def isStaticTestNameElement(testNameElement: ScExpression): Boolean =
    testNameElement match {
      case _: ScInterpolatedStringLiteral => false
      case _: ScStringLiteral             => true
      case _                              => false
    }

  private def staticTestNameOfParameter(testNameElement: ScExpression): Option[String] =
    testNameElement match {
      case _: ScInterpolatedStringLiteral => None
      case literal: ScStringLiteral       => Some(literal.contentText)
      case _                              => None
    }

  private def testNameElement(testRef: ScReferenceExpression): Option[ScExpression] =
    testRef.getParent match {
      case call: ScMethodCall =>
        testNameElement(call)
      case _ =>
        None
    }

  private def testNameElement(testMethodCall: ScMethodCall): Option[ScExpression] =
    testMethodCall.args.exprs.headOption
}
