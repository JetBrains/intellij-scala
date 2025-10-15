package org.jetbrains.plugins.scala.lang
package completion
package postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion, WithIndexingMode}
import org.junit.Test

import java.nio.file.Path

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ScalaMatchPostfixTemplateTest extends PostfixTemplateTest {

  override def testPath(): Path = super.testPath() / "match"

  @Test
  def testSimple(): Unit = doTest()

  @Test
  def testInnerMatch(): Unit = doTest()

  @Test
  def testInfixExpr(): Unit = doTest()

  @Test
  def testInInfixExpr(): Unit = doTest()

  @Test
  def testInnerMatchInfixExpr(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveSealed(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveJavaEnum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveScalaEnum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveScalaEnum2(): Unit = doTest()

  @Test
  def testNoFunctionExprParent(): Unit = doNotApplicableTest()

  @Test
  def testNoBlockParent(): Unit = doNotApplicableTest()
}
