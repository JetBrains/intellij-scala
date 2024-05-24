package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.NeedsIndex
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithAllIndexingModes, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithAllIndexingModes
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

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveSealed(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveJavaEnum(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveScala2Enum(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveScala2Enum2(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveScala3Enum(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveScala3EnumInTheMiddle(): Unit = doTest()

  def testNoFunctionExprParent(): Unit = doNotApplicableTest()

  def testNoBlockParent(): Unit = doNotApplicableTest()
}
