package org.jetbrains.plugins.scala
package compiler.highlighting

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

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
        range = Some(new TextRange(48, 58)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = "type mismatch;"
      )
    )
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
      range = Some(new TextRange(1, 55)),
      quickFixDescriptions = Nil,
      msgPrefix = "class AbstractMethodInClassError needs to be abstract"
    ))
  )

  def testWrongReturnType(): Unit = runTestCase(
    fileName = "WrongReturnType.scala",
    content =
      """object WrongReturnType {
        |  def fn1(n: Int): String = fn2(n)
        |  def fn2(n: Int): Int = n
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(new TextRange(53, 59)),
      quickFixDescriptions = Nil,
      msgPrefix = "type mismatch;"
    ))
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
  override implicit def version: ScalaVersion = ScalaVersion.Latest.Scala_3_RC

  def testUnusedImports(): Unit = {
    setCompilerOptions("-Wunused:imports")

    def highlighting(startOffset: Int, endOffset: Int): ExpectedHighlighting =
      ExpectedHighlighting(
        severity = HighlightSeverity.WARNING,
        range = Some(TextRange.create(startOffset, endOffset)),
        quickFixDescriptions = List(QuickFixBundle.message("optimize.imports.fix")),
        msgPrefix = ScalaInspectionBundle.message("unused.import.statement")
      )

    runTestCase(
      fileName = "UnusedImportsWithFlag.scala",
      content =
        """import scala.util.control.*
          |import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
          |import scala.collection.mutable.Set
          |
          |class UnusedImportsWithFlag {
          |  val long = new AtomicLong()
          |}""".stripMargin,
      expectedResult = expectedResult(highlighting(0, 27), highlighting(64, 77), highlighting(91, 126))
    )
  }
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
        msgPrefix = "Found:    String"
      )
    )
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

  def testWrongReturnType(): Unit = runTestCase(
    fileName = "WrongReturnType.scala",
    content =
      """def fn1(n: Int): String = fn2(n)
        |def fn2(n: Int): Int = n
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(new TextRange(26, 32)),
      quickFixDescriptions = Nil,
      msgPrefix = "Found:    Int"
    ))
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
}
