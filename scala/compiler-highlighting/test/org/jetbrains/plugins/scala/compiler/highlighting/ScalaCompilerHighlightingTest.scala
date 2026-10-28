package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class ScalaCompilerHighlightingTest_2_13 extends ScalaCompilerHighlightingTestBase with ScalaCompilerHighlightingCommonScala2Scala3Test {

  @Test
  def testFunctionLiteral(): Unit = runTestFunctionLiteral(48)

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

  @Test
  def testFunctionLiteral_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestFunctionLiteral(50)
  }

  @Test
  def testWrongReturnType(): Unit = runTestWrongReturnType(53)

  @Test
  def testWrongReturnType_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestWrongReturnType(56)
  }

  private def runTestWrongReturnType(startOffset: Int): Unit = runTestCase(
    fileName = "WrongReturnType.scala",
    logTimestamps = true,
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

  @Test
  def testUnusedLocalDefinitions(): Unit = runTestUnusedLocalDefinitions()

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

  @Test
  def testUnusedLocalDefinitions_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestUnusedLocalDefinitions()
  }

  @Test
  def testCompilationWithParserError(): Unit = {
    runTestCompilationWithParserError()
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

  @Test
  def testCompilationWithParserError_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestCompilationWithParserError()
  }

  @Test
  def testTooManyParameter(): Unit = runTestTooManyParameter()

  @Test
  def testTooManyParameter_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestTooManyParameter()
  }

  protected def runTestTooManyParameter(): Unit = {
    @Language("Scala")
    val fileText =
      """
        |object Test {
        |  def test1(i: Int): Unit = ()
        |  def test2(i: Int): Unit = ()
        |
        |  test1(1)
        |  test1(1, 2)
        |  test1(1, 2, 3)
        |
        |  test2(1)
        |  test2(1, 2)
        |  test2(1, 2, 3)
        |}
        |""".stripMargin

    runTestCase(
      "tooMany.scala",
      fileText,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(100, 101)),
          quickFixDescriptions = Seq("Add parameter to method 'test1'"),
          msgPrefix = "too many arguments (found 2, expected 1) for method test1: (i: Int): Unit"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(114, 115)),
          quickFixDescriptions = Seq("Add parameters to method 'test1'"),
          msgPrefix = "too many arguments (found 3, expected 1) for method test1: (i: Int): Unit"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(143, 144)),
          quickFixDescriptions = Seq("Add parameter to method 'test2'"),
          msgPrefix = "too many arguments (found 2, expected 1) for method test2: (i: Int): Unit"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(157, 158)),
          quickFixDescriptions = Seq("Add parameters to method 'test2'"),
          msgPrefix = "too many arguments (found 3, expected 1) for method test2: (i: Int): Unit"
        ),
      )
    )
  }

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ScalaCompilerHighlightingTest_3_0 extends ScalaCompilerHighlightingTest_3 {

  @Test
  def testCompilationWithParserError(): Unit = {
    runTestCompilationWithParserError()
  }

  @Test
  def testCompilationWithParserError_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestCompilationWithParserError()
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
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(97, 97)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "unindent expected, but eof found"
        )
      )
    )
  }

  @Test
  def testEof(): Unit = {
    runTestEof()
  }

  private def runTestEof(): Unit = {
    runTestCase(
      fileName = "Eof.scala",
      content =
        """object Eof:
          |  def eof(): Unit = {
          |    println("Hello, world!")
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(63, 63)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "'}' expected, but eof found"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(63, 63)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "Line is indented too far to the left, or a `}` is missing"
        )
      )
    )
  }

  @Test
  def testEof_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestEof()
  }

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_0
}

class ScalaCompilerHighlightingTest_3_1 extends ScalaCompilerHighlightingTest_3 {

  @Test
  def testCompilationWithParserError(): Unit = {
    runTestCompilationWithParserError()
  }

  @Test
  def testCompilationWithParserError_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestCompilationWithParserError()
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

  @Test
  def testEof(): Unit = {
    runTestEof()
  }

  private def runTestEof(): Unit = {
    runTestCase(
      fileName = "Eof.scala",
      content =
        """object Eof:
          |  def eof(): Unit = {
          |    println("Hello, world!")
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(63, 63)),
          quickFixDescriptions = Seq.empty,
          msgPrefix = "'}' expected, but eof found"
        )
      )
    )
  }

  @Test
  def testEof_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestEof()
  }

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_1
}

class ScalaCompilerHighlightingTest_3_2 extends ScalaCompilerHighlightingTest_3_1 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_2
}

// Features introduced in Scala 3.3.0, shared by the 3.3 LTS line and by Scala Next (3.4+). Behaviour that was later
// backported to the LTS line only must not leak into the 3.4-3.8 tests, so it lives in ScalaCompilerHighlightingTest_3_3.
abstract class ScalaCompilerHighlightingTest_3_3_Base extends ScalaCompilerHighlightingTest_3_2 {

  @Test
  def testUnusedImports(): Unit = runTestUnusedImports()

  @Test
  def testUnusedImports_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestUnusedImports()
  }

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

  @Test
  def testAutomaticUnusedImports(): Unit = runTestAutomaticUnusedImports()

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
      expectedResult = expectedResult(highlighting(0, 27), highlighting(64, 77), highlighting(91, 126)),
      // Unused imports are reported only by the document compilation (-Wunused:imports), which is the second
      // cycle after the incremental compilation, so we must wait for two highlighting cycles.
      compileCycles = 2,
    )
  }

  @Test
  def testAutomaticUnusedImports_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestAutomaticUnusedImports()
  }

  @Test
  def testUnusedLocalDefinitions(): Unit = runTestUnusedLocalDefinitions()

  @Test
  def testUnusedLocalDefinitions_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestUnusedLocalDefinitions()
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

  override def testWarningHighlighting(): Unit = {
    runTestWarningHighlighting(Seq("Insert missing cases (1)"))
  }

  @Test
  override def testWarningHighlighting_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestWarningHighlighting(Seq("Insert missing cases (1)"))
  }
}

class ScalaCompilerHighlightingTest_3_3 extends ScalaCompilerHighlightingTest_3_3_Base with ScalaCompilerHighlightingAddMissingMethodsTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_3

  override protected def anonymousGivenErrorSpansSyntheticClassName: Boolean = true
}

class ScalaCompilerHighlightingTest_3_RC extends ScalaCompilerHighlightingTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_LTS_RC
}

class ScalaCompilerHighlightingTest_3_4 extends ScalaCompilerHighlightingTest_3_3_Base {
  // SCL-23325
  @Test
  def testImportImplicits(): Unit = runTestCase(
    fileName = "blub/ImportImplicits.scala",
    content =
      """package blub
        |
        |class Example {
        |  summon[String]
        |}
        |
        |object ImplicitsStatic1 {
        |  implicit val s3: String = ???
        |}
        |object ImplicitsStatic2 {
        |  implicit val s3: String = ???
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(TextRange.create(45, 46)),
      quickFixDescriptions = Seq("Import 'blub.ImplicitsStatic1.s3'", "Import 'blub.ImplicitsStatic2.s3'"),
      msgPrefix = "No given instance of type String was found for parameter x of method summon in object Predef"
    ))
  )

  @Test
  def testImportExtension(): Unit = runTestCase(
    fileName = "blub/ImportExtension.scala",
    content =
      """package blub
        |
        |class Example {
        |  "test".test
        |}
        |
        |object ExtensionHolder {
        |  extension (i: String) def test = 3
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(TextRange.create(39, 43)),
      quickFixDescriptions = Seq("Import 'blub.ExtensionHolder.test'"),
      msgPrefix = "value test is not a member of String, but could be made available as an extension method."
    ))
  )

  @Test
  def testImportConversion(): Unit = runTestCase(
    fileName = "blub/ImportExtension.scala",
    content =
      """package blub
        |
        |class Example {
        |  val i: String = 1
        |}
        |
        |object ConversionHolder {
        |  given Conversion[Int, String] = _.toString
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(TextRange.create(48, 49)),
      quickFixDescriptions = Seq("Import 'blub.ConversionHolder.given_Conversion_Int_String'"),
      msgPrefix = "Found:    (1 : Int)\nRequired: String"
    ))
  )

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4
}

class ScalaCompilerHighlightingTest_3_5 extends ScalaCompilerHighlightingTest_3_4 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5
}

class ScalaCompilerHighlightingTest_3_6 extends ScalaCompilerHighlightingTest_3_5 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_6
}

class ScalaCompilerHighlightingTest_3_7 extends ScalaCompilerHighlightingTest_3_6 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_7

  override protected def anonymousGivenErrorSpansSyntheticClassName: Boolean = true
}

class ScalaCompilerHighlightingTest_3_8 extends ScalaCompilerHighlightingTest_3_7 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_8
}

class ScalaCompilerHighlightingTest_3_9 extends ScalaCompilerHighlightingTest_3_8 with ScalaCompilerHighlightingAddMissingMethodsTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_9
}

class ScalaCompilerHighlightingTest_3_10 extends ScalaCompilerHighlightingTest_3_9 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_10
}

class ScalaCompilerHighlightingTest_3_Next_RC extends ScalaCompilerHighlightingTest_3_10 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}

// Scala 3.9.0-RC1 enriched the "object creation impossible" / "needs to be abstract" compiler messages (they now
// spell out which members are missing) and ships its own code actions for them, so the plugin no longer registers its
// quickfixes (see HighlightInfoFactory). The change was backported to Scala 3.3.9. As a result the offered quickfixes
// changed compared to earlier versions: "Implement members" became "Add missing methods", and "Make '<name>' abstract"
// now quotes the name with backticks. The highlighted ranges and the message prefixes are unchanged.
trait ScalaCompilerHighlightingAddMissingMethodsTest extends ScalaCompilerHighlightingCommonScala2Scala3Test {
  self: ScalaCompilerHighlightingTestBase =>

  private val addMissingMethods = "Add missing methods"

  private def makeAbstract(className: String) = s"Make `$className` abstract"

  override protected def objectCreationImpossibleQuickFixes: Seq[String] = Seq(addMissingMethods)

  override protected def classNeedsToBeAbstractQuickFixes(className: String): Seq[String] =
    Seq(addMissingMethods, makeAbstract(className))

  override protected def abstractMethodInClassQuickFixes(className: String): Seq[String] =
    Seq(addMissingMethods, makeAbstract(className))
}

@RunWith(classOf[JUnit4])
abstract class ScalaCompilerHighlightingTest_3 extends ScalaCompilerHighlightingTestBase with ScalaCompilerHighlightingCommonScala2Scala3Test {

  @Test
  def testImportTypeFix(): Unit = runTestImportTypeFix()

  @Test
  def testImportTypeFix_UseCompilerRanges(): Unit = withUseCompilerRangesDisabled {
    runTestImportTypeFix()
  }

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

  @Test
  def testImportMemberFix(): Unit = runTestImportMemberFix()

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

  @Test
  def testImportMemberFix_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestImportMemberFix()
  }

  @Test
  def testFunctionLiteral(): Unit = runTestFunctionLiteral()

  @Test
  def testFunctionLiteral_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestFunctionLiteral()
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

  @Test
  def testWrongReturnType(): Unit = runTestWrongReturnType(26)

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

  @Test
  def testWrongReturnType_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestWrongReturnType(29)
  }

  @Test
  def testTooManyParameter(): Unit = runTestTooManyParameter()

  @Test
  def testTooManyParameter_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestTooManyParameter()
  }

  protected def runTestTooManyParameter(): Unit = {
    @Language("Scala")
    val fileText =
      """
        |object Test {
        |  def test1(i: Int): Unit = ()
        |  def test2(i: Int): Unit = ()
        |
        |  test1(1)
        |  test1(1, 2)
        |  test1(1, 2, 3)
        |
        |  test2(1)
        |  test2(1, 2)
        |  test2(1, 2, 3)
        |}
        |""".stripMargin

    runTestCase(
      "tooMany.scala",
      fileText,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(97, 101)),
          quickFixDescriptions = Seq("Add parameter to method 'test1'"),
          msgPrefix = "Found:"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(111, 118)),
          quickFixDescriptions = Seq("Add parameters to method 'test1'"),
          msgPrefix = "Found:"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(140, 144)),
          quickFixDescriptions = Seq("Add parameter to method 'test2'"),
          msgPrefix = "Found:"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(154, 161)),
          quickFixDescriptions = Seq("Add parameters to method 'test2'"),
          msgPrefix = "Found:"
        ),
      )
    )
  }

  @Test
  def testMultipleErrors(): Unit = runTestMultipleErrors(alreadyDefinedStartOffset = 69)

  // SCL-25244
  private def runTestMultipleErrors(alreadyDefinedStartOffset: Int): Unit = runTestCase(
    fileName = "MultipleErrors.scala",
    content =
      """object MultipleErrors:
        |  class C(x: Int)
        |  class Text(str: String)
        |  case class C(x: Text)
        |  def Test() =
        |    val c = C("a")
        |    val d = new C("b")
        |""".stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(TextRange.create(alreadyDefinedStartOffset, 90)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = "C is already defined as class C"
      ),
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(TextRange.create(120, 123)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = "Found:"
      ),
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(TextRange.create(143, 146)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = "Found:"
      )
    )
  )

  @Test
  def testMultipleErrors_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestMultipleErrors(alreadyDefinedStartOffset = 80)
  }

  // Since Scala 3.7.4 (backported to Scala 3.3.8 LTS), "object creation impossible" on an anonymous given instance
  // (`given Something1(13) with {}`) is reported with the span of the synthetic class name (`given_Something1`),
  // anchored at the first parent type. Earlier versions highlight only the first parent type name (`Something1`).
  protected def anonymousGivenErrorSpansSyntheticClassName: Boolean = false

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

    // see anonymousGivenErrorSpansSyntheticClassName
    def givenRange(startOffset: Int, parentTypeName: String, syntheticClassName: String): TextRange =
      TextRange.from(startOffset, if (anonymousGivenErrorSpansSyntheticClassName) syntheticClassName.length else parentTypeName.length)

    runTestCase(
      fileName = "Test.scala",
      content = fileText,
      expectedResult = expectedResult(
        // val v1 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `new` in Scala 3
          range = Some(TextRange.create(186, 189)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // val v2 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `new` in Scala 3
          range = Some(TextRange.create(218, 221)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // val v3 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `new` in Scala 3
          range = Some(TextRange.create(250, 253)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // class C1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C1`
          range = Some(TextRange.create(299, 301)),
          quickFixDescriptions = classNeedsToBeAbstractQuickFixes("C1"),
          msgPrefix = classNeedsToBeAbstract("C1"),
        ),
        // class C2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C2`
          range = Some(TextRange.create(332, 334)),
          quickFixDescriptions = classNeedsToBeAbstractQuickFixes("C2"),
          msgPrefix = classNeedsToBeAbstract("C2"),
        ),
        // class C3 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C3`
          range = Some(TextRange.create(365, 367)),
          quickFixDescriptions = classNeedsToBeAbstractQuickFixes("C3"),
          msgPrefix = classNeedsToBeAbstract("C3"),
        ),
        // object O1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O1`
          range = Some(TextRange.create(419, 421)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // object O2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O2`
          range = Some(TextRange.create(453, 455)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // object O3 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O3`
          range = Some(TextRange.create(488, 490)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // given Something1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(givenRange(543, "Something1", "given_Something1")),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // given Something2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(givenRange(574, "Something2", "given_Something2")),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // given Something1(...) with Something2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(givenRange(605, "Something1", "given_Something1_Something2")),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
      )
    )
  }
}

trait ScalaCompilerHighlightingCommonScala2Scala3Test {
  self: ScalaCompilerHighlightingTestBase =>

  // Quickfixes offered on the "object creation impossible" / "class X needs to be abstract" errors. Scala 2 and Scala 3
  // before 3.3.9 / 3.9.0 report them without code actions, so the plugin registers its own ones
  // (ScTemplateDefinitionAnnotator.needsToBeAbstractFixes); see ScalaCompilerHighlightingAddMissingMethodsTest for the
  // newer versions.
  protected def objectCreationImpossibleQuickFixes: Seq[String] = Seq("Implement members")

  protected def classNeedsToBeAbstractQuickFixes(className: String): Seq[String] =
    Seq(s"Make '$className' abstract", "Implement members")

  // The class declares the abstract member itself, so there is nothing to implement
  protected def abstractMethodInClassQuickFixes(className: String): Seq[String] = Seq(s"Make '$className' abstract")

  @Test
  def testWarningHighlighting(): Unit = runTestWarningHighlighting(Seq.empty)

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

  @Test
  def testWarningHighlighting_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestWarningHighlighting(Seq.empty)
  }

  @Test
  def testAbstractMethodInClass(): Unit = runTestAbstractMethodInClass()

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
      quickFixDescriptions = abstractMethodInClassQuickFixes("AbstractMethodInClassError"),
      msgPrefix = "class AbstractMethodInClassError needs to be abstract"
    ))
  )

  @Test
  def testAbstractMethodInClass_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestAbstractMethodInClass()
  }

  @Test
  def testNotImplementedMembers(): Unit = runTestNotImplementedMembers()

  @Test
  def testNotImplementedMembers_UseCompilerRangesDisabled(): Unit = withUseCompilerRangesDisabled {
    runTestNotImplementedMembers()
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

    runTestCase(
      fileName = "Test.scala",
      content = fileText,
      expectedResult = expectedResult(
        // val v1 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something1 {}` in Scala 2
          range = Some(TextRange.create(181, 194)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // val v2 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something2 {}` in Scala 2
          range = Some(TextRange.create(210, 223)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // val v3 = ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `Something1 with Something2 {}` in Scala 2
          range = Some(TextRange.create(239, 268)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // class C1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C1`
          range = Some(TextRange.create(278, 280)),
          quickFixDescriptions = classNeedsToBeAbstractQuickFixes("C1"),
          msgPrefix = classNeedsToBeAbstract("C1"),
        ),
        // class C2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C2`
          range = Some(TextRange.create(308, 310)),
          quickFixDescriptions = classNeedsToBeAbstractQuickFixes("C2"),
          msgPrefix = classNeedsToBeAbstract("C2"),
        ),
        // class C3 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `C3`
          range = Some(TextRange.create(338, 340)),
          quickFixDescriptions = classNeedsToBeAbstractQuickFixes("C3"),
          msgPrefix = classNeedsToBeAbstract("C3"),
        ),
        // object O1 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O1`
          range = Some(TextRange.create(386, 388)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // object O2 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O2`
          range = Some(TextRange.create(417, 419)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
        // object O3 ...
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          // highlights `O3`
          range = Some(TextRange.create(448, 450)),
          quickFixDescriptions = objectCreationImpossibleQuickFixes,
          msgPrefix = objectCreationImpossible,
        ),
      )
    )
  }
}
