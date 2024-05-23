package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.NeedsIndex
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithIndexingModes, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithIndexingModes
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

  @NeedsIndex.SmartMode(reason = "ScExpression.`type`() doesn't work in DumbMode")
  def testExhaustiveSealed(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "ScExpression.`type`() doesn't work in DumbMode")
  def testExhaustiveJavaEnum(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "ScExpression.`type`() doesn't work in DumbMode")
  def testExhaustiveScala2Enum(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "ScExpression.`type`() doesn't work in DumbMode")
  def testExhaustiveScala2Enum2(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "ScExpression.`type`() doesn't work in DumbMode")
  def testExhaustiveScala3Enum(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "ScExpression.`type`() doesn't work in DumbMode")
  def testExhaustiveScala3EnumInTheMiddle(): Unit = doTest()

  def testNoFunctionExprParent(): Unit = doNotApplicableTest()

  def testNoBlockParent(): Unit = doNotApplicableTest()
}
