package org.jetbrains.plugins.scala
package compiler.highlighting

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.intellij.lang.annotations.Language
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

  private def runTestCompilationWithParserError(): Unit = {
    runTestCase(
      fileName = "ParserError.scala",
      content =
        """object ParserError {
          |  def parserError(): Unit = {
          |    val x = Seq(1, 2, 3
          |    val y = Seq(2, 3, 4)
          |  }
          |}
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(79, 82)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "')' expected but 'val' found."
        )
      )
    )
  }

  def testCompilationWithParserError(): Unit = {
    runTestCompilationWithParserError()
  }

  def testCompilationWithParserError_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestCompilationWithParserError()
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

class ScalaCompilerHighlightingTest_3_5 extends ScalaCompilerHighlightingTest_3_4 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5
}

class ScalaCompilerHighlightingTest_3_6 extends ScalaCompilerHighlightingTest_3_5 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_6
}

class ScalaCompilerHighlightingTest_3_RC extends ScalaCompilerHighlightingTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_LTS_RC
}

class ScalaCompilerHighlightingTest_3_Next_RC extends ScalaCompilerHighlightingTest_3_4 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
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

  private def runTestCompilationWithParserError(): Unit = {
    runTestCase(
      fileName = "ParserError.scala",
      content =
        """object ParserError:
          |  def parserError(): Unit =
          |    val x = Seq(1, 2, 3
          |    val y = Seq(2, 3, 4)
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(76, 79)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "')' expected, but 'val' found"
        )
      )
    )
  }

  def testCompilationWithParserError(): Unit = {
    runTestCompilationWithParserError()
  }

  def testCompilationWithParserError_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestCompilationWithParserError()
  }

  // SCL-19751
  override protected def runTestNotImplementedMembers(): Unit = {
    @Language("Scala 3")
    val fileText =
      """
        |trait Something1(x: Int):
        |  def implementMe(): Unit
        |
        |trait Something2(y: Int):
        |  def implementMe1(): Unit
        |  def implementMe2(): Unit
        |  def implementMe3(): Unit
        |
        |object Test:
        |  val v1 = new Something1(1) {}
        |  val v2 = new Something2(2) {}
        |  val v3 = new Something1(3) with Something2(4) {}
        |
        |  class C1 extends Something1(5)
        |  class C2 extends Something2(6)
        |  class C3 extends Something1(7) with Something2(8)
        |
        |  object O1 extends Something1(9)
        |  object O2 extends Something2(10)
        |  object O3 extends Something1(11) with Something2(12)
        |
        |  given Something1(13) with {}
        |  given Something2(14) with {}
        |  given Something1(15) with Something2(16) with {}
        |end Test
        |""".stripMargin

    val objectCreationImpossible = "object creation impossible,"
    def classNeedsToBeAbstract(name: String) = s"class $name needs to be abstract,"

    def makeAbstract(name: String) = s"Make '$name' abstract"
    val implementMembers = "Implement members"

    runTestCase(
      fileName = "Test.scala",
      content = fileText,
      expectedResult = expectedResult(
        // val v1 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `new` in Scala 3
          range = Some(TextRange.create(186, 189)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // val v2 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `new` in Scala 3
          range = Some(TextRange.create(218, 221)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // val v3 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `new` in Scala 3
          range = Some(TextRange.create(250, 253)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // class C1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C1`
          range = Some(TextRange.create(299, 301)),
          quickFixDescriptions = Seq(makeAbstract("C1"), implementMembers),
          msgPrefix = classNeedsToBeAbstract("C1"),
        ),
        // class C2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C2`
          range = Some(TextRange.create(332, 334)),
          quickFixDescriptions = Seq(makeAbstract("C2"), implementMembers),
          msgPrefix = classNeedsToBeAbstract("C2"),
        ),
        // class C3 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C3`
          range = Some(TextRange.create(365, 367)),
          quickFixDescriptions = Seq(makeAbstract("C3"), implementMembers),
          msgPrefix = classNeedsToBeAbstract("C3"),
        ),
        // object O1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O1`
          range = Some(TextRange.create(419, 421)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // object O2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O2`
          range = Some(TextRange.create(453, 455)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // object O3 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O3`
          range = Some(TextRange.create(488, 490)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // given Something1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something1`
          range = Some(TextRange.create(543, 553)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // given Something2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something2`
          range = Some(TextRange.create(574, 584)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // given Something1(...) with Something2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something1`
          range = Some(TextRange.create(605, 615)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
      )
    )
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

  protected def runTestAbstractMethodInClass(): Unit = runTestCase(
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
      quickFixDescriptions = Seq("Make 'AbstractMethodInClassError' abstract"),
      msgPrefix = "class AbstractMethodInClassError needs to be abstract"
    ))
  )

  def testAbstractMethodInClass(): Unit = runTestAbstractMethodInClass()

  def testAbstractMethodInClass_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestAbstractMethodInClass()
  }

  // SCL-19751
  protected def runTestNotImplementedMembers(): Unit = {
    @Language("Scala")
    val fileText =
      """
        |trait Something1 {
        |  def implementMe(): Unit
        |}
        |
        |trait Something2 {
        |  def implementMe1(): Unit
        |  def implementMe2(): Unit
        |  def implementMe3(): Unit
        |}
        |
        |object Test {
        |  val v1 = new Something1 {}
        |  val v2 = new Something2 {}
        |  val v3 = new Something1 with Something2 {}
        |
        |  class C1 extends Something1
        |  class C2 extends Something2
        |  class C3 extends Something1 with Something2
        |
        |  object O1 extends Something1
        |  object O2 extends Something2
        |  object O3 extends Something1 with Something2
        |}
        |""".stripMargin

    val objectCreationImpossible = "object creation impossible."
    def classNeedsToBeAbstract(name: String) = s"class $name needs to be abstract."

    def makeAbstract(name: String) = s"Make '$name' abstract"
    val implementMembers = "Implement members"

    runTestCase(
      fileName = "Test.scala",
      content = fileText,
      expectedResult = expectedResult(
        // val v1 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something1 {}` in Scala 2
          range = Some(TextRange.create(181, 194)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // val v2 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something2 {}` in Scala 2
          range = Some(TextRange.create(210, 223)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // val v3 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something1 with Something2 {}` in Scala 2
          range = Some(TextRange.create(239, 268)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // class C1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C1`
          range = Some(TextRange.create(278, 280)),
          quickFixDescriptions = Seq(makeAbstract("C1"), implementMembers),
          msgPrefix = classNeedsToBeAbstract("C1"),
        ),
        // class C2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C2`
          range = Some(TextRange.create(308, 310)),
          quickFixDescriptions = Seq(makeAbstract("C2"), implementMembers),
          msgPrefix = classNeedsToBeAbstract("C2"),
        ),
        // class C3 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C3`
          range = Some(TextRange.create(338, 340)),
          quickFixDescriptions = Seq(makeAbstract("C3"), implementMembers),
          msgPrefix = classNeedsToBeAbstract("C3"),
        ),
        // object O1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O1`
          range = Some(TextRange.create(386, 388)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // object O2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O2`
          range = Some(TextRange.create(417, 419)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
        // object O3 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O3`
          range = Some(TextRange.create(448, 450)),
          quickFixDescriptions = Seq(implementMembers),
          msgPrefix = objectCreationImpossible,
        ),
      )
    )
  }

  def testNotImplementedMembers(): Unit = runTestNotImplementedMembers()

  def testNotImplementedMembers_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestNotImplementedMembers()
  }
}
