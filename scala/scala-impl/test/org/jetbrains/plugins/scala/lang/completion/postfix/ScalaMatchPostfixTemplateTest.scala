package org.jetbrains.plugins.scala.lang
package completion
package postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion, WithIndexingMode}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ScalaMatchPostfixTemplateTest extends PostfixTemplateTest {

  override def testPath(): String = super.testPath() + "match/"

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
  def testExhaustiveScalaEnum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  def testExhaustiveScalaEnum2(): Unit = doTest()

  def testNoFunctionExprParent(): Unit = doNotApplicableTest()

  def testNoBlockParent(): Unit = doNotApplicableTest()
}
