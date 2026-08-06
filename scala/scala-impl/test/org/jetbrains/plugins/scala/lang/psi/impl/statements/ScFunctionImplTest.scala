package org.jetbrains.plugins.scala.lang.psi.impl.statements

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.junit.ComparisonFailure

class ScFunctionImplTest extends ScalaLightCodeInsightFixtureTestCase {

  def testNavigationElementForOverloadedMethod(): Unit = {
    configureScalaFromFileText(
      """package example
        |
        |class MyClass {
        |  def foo(): String = ???
        |  def foo(x: String => Int): String = ???
        |}
        |""".stripMargin
    )

    val methods = getFile.elements.filterByType[ScFunction]
    methods.foreach(assertSourceMirrorMemberIsTheSame)
  }

  def testNavigationElementForOverloadedMethodWithTypeWrappedInParenthesis(): Unit = {
    configureScalaFromFileText(
      """package example
        |
        |//noinspection ScalaUnnecessaryParentheses
        |class MyClass {
        |  def foo(): String = ???
        |  def foo(x: (String => Int)): String = ???
        |}
        |""".stripMargin
    )

    val methods = getFile.elements.filterByType[ScFunction]
    methods.foreach(assertSourceMirrorMemberIsTheSame)
  }

  private def assertSourceMirrorMemberIsTheSame(method: ScFunction): Unit = {
    val mirrorMember = method.getSourceMirrorMemberForTests
    if (method ne mirrorMember) {
      throw new ComparisonFailure(
        s"Expected mirror member to be the same as the original method, but they were different: ${method.getText}",
        method.getText,
        mirrorMember.getText
      )
    }
  }
}