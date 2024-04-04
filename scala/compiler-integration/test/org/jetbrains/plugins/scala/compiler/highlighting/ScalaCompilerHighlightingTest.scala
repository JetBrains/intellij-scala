package org.jetbrains.plugins.scala
package compiler.highlighting

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

class ScalaCompilerHighlightingTest_2_13 extends ScalaCompilerHighlightingTestBase with ScalaCompilerHighlightingCommonScala2Scala3Test {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  private def runTestFunctionLiteral(startOffset: Int): Unit = runTestCase(
    fileName = "FunctionLiteral.scala",
    content =
      """object FunctionLiteral {
        |  val fn: Int => Int = _.toString
        |}
        |""".stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(TextRange.create(startOffset, 58)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = "type mismatch;"
      )
    )
  )

  def testFunctionLiteral(): Unit = runTestFunctionLiteral(48)

  def testFunctionLiteral_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestFunctionLiteral(50)
  }

  private def runTestErrorHighlighting(): Unit = runTestCase(
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

  def testErrorHighlighting(): Unit = runTestErrorHighlighting()

  def testErrorHighlighting_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestErrorHighlighting()
  }

  private def runTestWrongReturnType(startOffset: Int): Unit = runTestCase(
    fileName = "WrongReturnType.scala",
    content =
      """object WrongReturnType {
        |  def fn1(n: Int): String = fn2(n)
        |  def fn2(n: Int): Int = n
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(TextRange.create(startOffset, 59)),
      quickFixDescriptions = Seq.empty,
      msgPrefix = "type mismatch;"
    ))
  )

  def testWrongReturnType(): Unit = runTestWrongReturnType(53)

  def testWrongReturnType_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestWrongReturnType(56)
  }

  private def runTestUnusedLocalDefinitions(): Unit = {
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
          range = Some(TextRange.create(69, 72)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "local val abc in method fn is never used"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(87, 90)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "local val dfe in method fn is never used"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(105, 108)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "local val xyz in method fn is never used"
        )
      )
    )
  }

  def testUnusedLocalDefinitions(): Unit = runTestUnusedLocalDefinitions()

  def testUnusedLocalDefinitions_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestUnusedLocalDefinitions()
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
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_3

  private def runTestUnusedImports(): Unit = {
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

  def testUnusedImports(): Unit = runTestUnusedImports()

  def testUnusedImports_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestUnusedImports()
  }

  private def runTestAutomaticUnusedImports(): Unit = {
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

  def testAutomaticUnusedImports(): Unit = runTestAutomaticUnusedImports()

  def testAutomaticUnusedImports_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestAutomaticUnusedImports()
  }

  private def runTestUnusedLocalDefinitions(): Unit = {
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

  def testUnusedLocalDefinitions(): Unit = runTestUnusedLocalDefinitions()

  def testUnusedLocalDefinitions_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestUnusedLocalDefinitions()
  }

  override def testWarningHighlighting(): Unit = {
    runTestWarningHighlighting(Seq("Insert missing cases (1)"))
  }

  override def testWarningHighlighting_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestWarningHighlighting(Seq("Insert missing cases (1)"))
  }
}

class ScalaCompilerHighlightingTest_3_4 extends ScalaCompilerHighlightingTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4
}

class ScalaCompilerHighlightingTest_3_RC extends ScalaCompilerHighlightingTest_3_4 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class ScalaCompilerHighlightingTest_3 extends ScalaCompilerHighlightingTestBase with ScalaCompilerHighlightingCommonScala2Scala3Test {

  private def runTestImportTypeFix(): Unit = runTestCase(
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

  def testImportTypeFix(): Unit = runTestImportTypeFix()

  def testImportTypeFix_UseCompilerRanges(): Unit = withUseCompilerRangesDisabled {
    runTestImportTypeFix()
  }

  private def runTestImportMemberFix(): Unit = runTestCase(
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

  def testImportMemberFix(): Unit = runTestImportMemberFix()

  def testImportMemberFix_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestImportMemberFix()
  }

  private def runTestFunctionLiteral(): Unit = runTestCase(
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

  def testFunctionLiteral(): Unit = runTestFunctionLiteral()

  def testFunctionLiteral_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestFunctionLiteral()
  }

  private def runTestErrorHighlighting(): Unit = runTestCase(
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

  def testErrorHighlighting(): Unit = runTestErrorHighlighting()

  def testErrorHighlighting_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestErrorHighlighting()
  }

  private def runTestWrongReturnType(startOffset: Int): Unit = runTestCase(
    fileName = "WrongReturnType.scala",
    content =
      """def fn1(n: Int): String = fn2(n)
        |def fn2(n: Int): Int = n
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(TextRange.create(startOffset, 32)),
      quickFixDescriptions = Seq.empty,
      msgPrefix = "Found:    Int"
    ))
  )

  def testWrongReturnType(): Unit = runTestWrongReturnType(26)

  def testWrongReturnType_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestWrongReturnType(29)
  }
}

trait ScalaCompilerHighlightingCommonScala2Scala3Test {
  self: ScalaCompilerHighlightingTestBase =>

  protected def runTestWarningHighlighting(quickFixDescriptions: Seq[String]): Unit = runTestCase(
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
      quickFixDescriptions = quickFixDescriptions,
      msgPrefix = "match may not be exhaustive"
    ))
  )

  def testWarningHighlighting(): Unit = runTestWarningHighlighting(Seq.empty)

  def testWarningHighlighting_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestWarningHighlighting(Seq.empty)
  }
}
