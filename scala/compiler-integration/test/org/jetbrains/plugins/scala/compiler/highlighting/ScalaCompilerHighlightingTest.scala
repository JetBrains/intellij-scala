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
        range = Some(TextRange.create(48, 58)),
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
      range = Some(TextRange.create(1, 55)),
      quickFixDescriptions = Seq.empty,
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
      range = Some(TextRange.create(53, 59)),
      quickFixDescriptions = Seq.empty,
      msgPrefix = "type mismatch;"
    ))
  )

  def testUnusedLocalDefinitions(): Unit = {
    setCompilerOptions("-Wunused:locals")

    runTestCase(
      fileName = "UnusedLocalDefinitions.scala",
      content =
        """object UnusedLocalDefinitions {
          |  def fn(n: Int): String = {
          |    val abc = 123
          |    val dfe = 456
          |    val xyz = 789
          |    n.toString
          |  }
          |}
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(65, 78)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "local val abc in method fn is never used"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(83, 96)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "local val dfe in method fn is never used"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(101, 114)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "local val xyz in method fn is never used"
        )
      )
    )
  }
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
  override implicit def version: ScalaVersion = ScalaVersion.Latest.Scala_3_3

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

  def testAutomaticUnusedImports(): Unit = {
    def highlighting(startOffset: Int, endOffset: Int): ExpectedHighlighting =
      ExpectedHighlighting(
        severity = HighlightSeverity.WARNING,
        range = Some(TextRange.create(startOffset, endOffset)),
        quickFixDescriptions = List(QuickFixBundle.message("optimize.imports.fix")),
        msgPrefix = ScalaInspectionBundle.message("unused.import.statement")
      )

    runTestCase(
      fileName = "AutomaticUnusedImports.scala",
      content =
        """import scala.util.control.*
          |import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
          |import scala.collection.mutable.Set
          |
          |class AutomaticUnusedImports {
          |  val long = new AtomicLong()
          |}""".stripMargin,
      expectedResult = expectedResult(highlighting(0, 27), highlighting(64, 77), highlighting(91, 126))
    )
  }

  def testUnusedLocalDefinitions(): Unit = {
    setCompilerOptions("-Wunused:locals")

    def expectedHighlighting(startOffset: Int, endOffset: Int): ExpectedHighlighting =
      ExpectedHighlighting(
        severity = HighlightSeverity.WARNING,
        range = Some(TextRange.create(startOffset, endOffset)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = "unused local definition"
      )

    runTestCase(
      fileName = "UnusedLocalDefinitions.scala",
      content =
        """def fn(n: Int): String =
          |  val abc = 123
          |  val dfe = 456
          |  val xyz = 789
          |  n.toString
          |""".stripMargin,
      expectedResult = expectedResult(
        expectedHighlighting(31, 34),
        expectedHighlighting(47, 50),
        expectedHighlighting(63, 66)
      )
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
      range = Some(TextRange.create(34, 51)),
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
      range = Some(TextRange.create(9, 16)),
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
        range = Some(TextRange.create(21, 31)),
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
      range = Some(TextRange.create(7, 33)),
      quickFixDescriptions = Seq.empty,
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
      range = Some(TextRange.create(26, 32)),
      quickFixDescriptions = Seq.empty,
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
      range = Some(TextRange.create(70, 76)),
      quickFixDescriptions = Seq.empty,
      msgPrefix = "match may not be exhaustive"
    ))
  )
}
