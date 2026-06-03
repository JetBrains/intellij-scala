package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
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
  val FunFixtureFqn = "munit.FunFixtures.FunFixture"

  val FunSuiteFqnList: List[String] = List(BaseFunSuiteFqn, FunSuiteFqn)
  val ScalaCheckSuiteFqnList: List[String] = List(ScalaCheckSuiteFqn)

  // Classes that declare the framework `test` / `property` methods.
  val TestMethodOwnerFqns: Set[String] =
    Set(BaseFunSuiteFqn, FunSuiteFqn, ScalaCheckSuiteFqn, FunFixtureFqn)

  /**
   * @param testRef element representing `test` node in FunSuite test definition:<br>
   *                '''test'''("my test 1") { ... }
   */
  def hasStaticTestName(testRef: ScReferenceExpression): Boolean =
    testNameElement(testRef).exists(isStaticTestNameElement)

  /**
   * @return whether `testRef` resolves to an MUnit framework `test` / `property`
   *         method — either declared directly on one of [[TestMethodOwnerFqns]],
   *         or overriding such a declaration. Filters out unrelated user methods
   *         (and overloads) that merely share the name `test` or `property`.
   */
  def hasMUnitTestMethodOwner(testRef: ScReferenceExpression): Boolean =
    testRef.resolve() match {
      case method: PsiMethod =>
        val candidates = LazyList.cons(method, method.findSuperMethods().to(LazyList))
        candidates.exists { m =>
          Option(m.containingClass)
            .exists(_.qualifiedNameOpt.exists(TestMethodOwnerFqns.contains))
        }
      case _ => false
    }

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
