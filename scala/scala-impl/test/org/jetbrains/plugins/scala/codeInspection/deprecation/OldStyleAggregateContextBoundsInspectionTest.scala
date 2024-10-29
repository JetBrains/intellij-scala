package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.implicits.OldStyleAggregateContextBoundsInspection

class OldStyleAggregateContextBoundsInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[OldStyleAggregateContextBoundsInspection]

  override protected def description: String =
    OldStyleAggregateContextBoundsInspection.description

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_6

  def testBoundsSimple(): Unit = {
    val code =
      s"""
         |class Foo[${START}A: Ord : Show$END](a: A)
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """
        |class Foo[A: {Ord, Show}](a: A)
        |""".stripMargin

    testQuickFix(code, expected, OldStyleAggregateContextBoundsInspection.fixDescription)
  }

  def testBoundsNamed(): Unit = {
    val code =
      s"""
         |class Foo[${START}A: Ord as O : Show as show$END](a: A)
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """
        |class Foo[A: {Ord as O, Show as show}](a: A)
        |""".stripMargin

    testQuickFix(code, expected, OldStyleAggregateContextBoundsInspection.fixDescription)
  }
}
