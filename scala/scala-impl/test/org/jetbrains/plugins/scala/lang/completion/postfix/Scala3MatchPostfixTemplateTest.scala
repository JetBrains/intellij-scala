package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.idea.TestFor
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion, WithIndexingMode}
import org.junit.Test

import java.nio.file.Path

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
final class Scala3MatchPostfixTemplateTest extends PostfixTemplateTest {
  override protected def setUp(): Unit = {
    super.setUp()
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }

  override def testPath(): Path = super.testPath() / "match3"

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
  @TestFor(issues = Array("SCL-24607", "SCL-24609"))
  def testExhaustiveSealed2(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveJavaEnum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveScala2Enum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveScala2Enum2(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveScala3Enum(): Unit = doTest()

  @WithIndexingMode(mode = IndexingMode.SMART, reason = "exhaustive match needs type inference")
  @Test
  def testExhaustiveScala3EnumInTheMiddle(): Unit = doTest()

  @Test
  def testNoFunctionExprParent(): Unit = doNotApplicableTest()

  @Test
  def testNoBlockParent(): Unit = doNotApplicableTest()
}
