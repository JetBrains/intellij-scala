package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion, WithIndexingMode}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
final class Scala3MatchPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "match3/"

  def testSimple(): Unit = doTest()

  def testInnerMatch(): Unit = doTest()

  def testInfixExpr(): Unit = doTest()

  def testInInfixExpr(): Unit = doTest()

  def testInnerMatchInfixExpr(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  def testExhaustiveSealed(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  def testExhaustiveJavaEnum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  def testExhaustiveScala2Enum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  def testExhaustiveScala2Enum2(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  def testExhaustiveScala3Enum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  def testExhaustiveScala3EnumInTheMiddle(): Unit = doTest()

  def testNoFunctionExprParent(): Unit = doNotApplicableTest()

  def testNoBlockParent(): Unit = doNotApplicableTest()
}
