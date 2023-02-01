package org.jetbrains.plugins.scala
package lang
package transformation
package calls

import org.junit.Ignore

abstract class CanonizeBlockArgumentTestBase extends TransformerTest(new CanonizeBlockArgument())

final class CanonizeBlockArgumentTest extends CanonizeBlockArgumentTestBase {
  def testMethodCall(): Unit = check(
    before = "f {A}",
    after = "f(A)"
  )()

  def testComplexExpression(): Unit = check(
    before = "f {A; B}",
    after = "f({A; B})"
  )()

  def testMultipleClauses(): Unit = check(
    before = "f(A) {B}",
    after = "f(A)(B)"
  )()

  def testExplicit(): Unit = check(
    before = "f(A)",
    after = "f(A)"
  )()

  def testMultilineBlock(): Unit = check(
    before =
      """f {
        |  1
        |  2
        |  3
        |}""".stripMargin,
    after =
      """f({
        |  1
        |  2
        |  3
        |})""".stripMargin
  )()

  // TODO test synthetic method
}

final class CanonizeBlockArgumentTestIgnored_Scala3 extends CanonizeBlockArgumentTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testFewerBraces(): Unit = check(
    before =
      """f:
        |  1
        |  2
        |  3
        |""".stripMargin,
    after =
      """f({
        |  1
        |  2
        |  3
        |})""".stripMargin
  )()

}

@Ignore("flaky tests")
class CanonizeBlockArgumentTestIgnored extends TransformerTest(new CanonizeBlockArgument()) {
  def testInfixExpression(): Unit = check(
    before = "O f {A}",
    after = "O f (A)"
  )()
}
