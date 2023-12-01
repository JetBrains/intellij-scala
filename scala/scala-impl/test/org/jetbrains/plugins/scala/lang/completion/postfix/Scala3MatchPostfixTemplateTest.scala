package org.jetbrains.plugins.scala.lang.completion.postfix

import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
final class Scala3MatchPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "match3/"

  def testSimple(): Unit = doTest()

  def testInnerMatch(): Unit = doTest()

  def testInfixExpr(): Unit = doTest()

  def testInInfixExpr(): Unit = doTest()

  def testInnerMatchInfixExpr(): Unit = doTest()

  def testExhaustiveSealed(): Unit = doTest()

  def testExhaustiveJavaEnum(): Unit = doTest()

  def testExhaustiveScala2Enum(): Unit = doTest()

  def testExhaustiveScala2Enum2(): Unit = doTest()

  def testExhaustiveScala3Enum(): Unit = doTest()

  def testExhaustiveScala3EnumInTheMiddle(): Unit = doTest()

  def testNoFunctionExprParent(): Unit = doNotApplicableTest()

  def testNoBlockParent(): Unit = doNotApplicableTest()
}
