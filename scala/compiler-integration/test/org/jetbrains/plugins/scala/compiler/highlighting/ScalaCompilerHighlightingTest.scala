package org.jetbrains.plugins.scala
package compiler.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

import scala.annotation.nowarn

class ScalaCompilerHighlightingTest_2_13 extends ScalaCompilerHighlightingTestBase with ScalaCompilerHighlightingCommonScala2Scala3Test {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testFunctionLiteral(): Unit = runTestCase(
    fileName = "FunctionLiteral.scala",
    content =
      """object FunctionLiteral {
        |  val fn: Int => Int = _.toString
        |}
        |""".stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(50, 58)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = s"type mismatch;"
      )
    )
  )
}

class ScalaCompilerHighlightingTest_3_0 extends ScalaCompilerHighlightingTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_0
}

class ScalaCompilerHighlightingTest_3_1 extends ScalaCompilerHighlightingTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_1
}

class ScalaCompilerHighlightingTest_3_2 extends ScalaCompilerHighlightingTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_2
}

class ScalaCompilerHighlightingTest_3_3 extends ScalaCompilerHighlightingTest_3 {
  @nowarn("cat=deprecation")
  override implicit def version: ScalaVersion = ScalaVersion.Latest.Scala_3_3_RC
}

abstract class ScalaCompilerHighlightingTest_3 extends ScalaCompilerHighlightingTestBase with ScalaCompilerHighlightingCommonScala2Scala3Test {

  def testImportTypeFix(): Unit = runTestCase(
    fileName = "ImportTypeFix.scala",
    content =
      """
        |trait ImportTypeFix {
        |  def map: ConcurrentHashMap[String, String] = ???
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(new TextRange(34, 51)),
      quickFixDescriptions = Seq("Import 'java.util.concurrent.ConcurrentHashMap'"),
      msgPrefix = "Not found: type ConcurrentHashMap"
    ))
  )

  def testImportMemberFix(): Unit = runTestCase(
    fileName = "ImportMemberFix.scala",
    content =
      """
        |val x = nextInt()
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(new TextRange(9, 16)),
      quickFixDescriptions = Seq("Import 'scala.util.Random.nextInt'", "Import as 'Random.nextInt'"),
      msgPrefix = "Not found: nextInt"
    ))
  )

  def testFunctionLiteral(): Unit = runTestCase(
    fileName = "FunctionLiteral.scala",
    content =
      """val fn: Int => Int = _.toString
        |""".stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(21, 31)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = s"Found:    String"
      )
    )
  )
}

trait ScalaCompilerHighlightingCommonScala2Scala3Test {
  self: ScalaCompilerHighlightingTestBase =>

  def testWarningHighlighting(): Unit = runTestCase(
    fileName = "ExhaustiveMatchWarning.scala",
    content =
      """
        |class ExhaustiveMatchWarning {
        |  val option: Option[Int] = Some(1)
        |  option match {
        |    case Some(_) =>
        |  }
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.WARNING,
      range = Some(new TextRange(70, 76)),
      quickFixDescriptions = Nil,
      msgPrefix = "match may not be exhaustive"
    ))
  )

  def testErrorHighlighting(): Unit = runTestCase(
    fileName = "AbstractMethodInClassError.scala",
    content =
      """
        |class AbstractMethodInClassError {
        |  def method: Int
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(new TextRange(7, 33)),
      quickFixDescriptions = Nil,
      msgPrefix = "class AbstractMethodInClassError needs to be abstract"
    ))
  )
}
