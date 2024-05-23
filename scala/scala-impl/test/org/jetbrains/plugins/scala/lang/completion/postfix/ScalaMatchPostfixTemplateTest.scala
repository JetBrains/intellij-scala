package org.jetbrains.plugins.scala.lang
package completion
package postfix

import com.intellij.testFramework.NeedsIndex
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithIndexingModes, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithIndexingModes
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class ScalaMatchPostfixTemplateTest extends PostfixTemplateTest {

  override def testPath(): String = super.testPath() + "match/"

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
  def testExhaustiveScalaEnum(): Unit = doTest()

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveScalaEnum2(): Unit = doTest()

  def testNoFunctionExprParent(): Unit = doNotApplicableTest()

  def testNoBlockParent(): Unit = doNotApplicableTest()
}
