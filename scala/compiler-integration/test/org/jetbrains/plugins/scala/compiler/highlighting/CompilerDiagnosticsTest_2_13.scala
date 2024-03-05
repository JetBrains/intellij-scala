package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion

class CompilerDiagnosticsTest_2_13 extends CompilerDiagnosticsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testImplicitDefinitionNeedsExplicitType(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "ImplicitDefinitionNeedsExplicitType.scala",
      content =
        """
          |object ImplicitDefinitionNeedsExplicitType {
          |  implicit def n = 5
          |}
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(48, 66)),
          quickFixDescriptions = Seq("Insert explicit type"),
          msgPrefix = "Implicit definition should have explicit type (inferred Int) [quickfixable]"
        )
      ),
      expectedContent =
        """
          |object ImplicitDefinitionNeedsExplicitType {
          |  implicit def n: Int = 5
          |}
          |""".stripMargin
    )
  }

  def testArgumentAdaptation(): Unit = {
    setCompilerOptions("-Xlint:adapted-args", "-deprecation")

    runCompilerDiagnosticsTest(
      fileName = "ArgumentAdaptation.scala",
      content =
        """
          |object ArgumentAdaptation {
          |  val test = Some(1, 2, 3, 4, 5)
          |}
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(42, 61)),
          quickFixDescriptions = Seq("Add wrapping parentheses"),
          msgPrefix = "adapted the argument list to the expected 5-tuple: add additional parens instead"
        )
      ),
      expectedContent =
        """
          |object ArgumentAdaptation {
          |  val test = Some((1, 2, 3, 4, 5))
          |}
          |""".stripMargin
    )
  }

  def testEtaExpansion(): Unit = {
    setCompilerOptions("-Xlint:deprecation,eta-zero,eta-sam")

    runCompilerDiagnosticsTest(
      fileName = "EtaExpansion.scala",
      content =
        """
          |object EtaExpansion {
          |  def foo() = ()
          |  val a = foo
          |
          |  def bar = ""
          |  val b = bar _
          |}
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(50, 53)),
          quickFixDescriptions = Seq("Add `()`"),
          msgPrefix = "Auto-application to `()` is deprecated."
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(80, 83)),
          quickFixDescriptions = Seq("Replace by function literal"),
          msgPrefix = "Methods without a parameter list and by-name params can no longer be converted to functions as `m _`, write a function literal `() => m` instead [quickfixable]"
        )
      ),
      expectedContent =
        """
          |object EtaExpansion {
          |  def foo() = ()
          |  val a = foo()
          |
          |  def bar = ""
          |  val b = () => bar
          |}
          |""".stripMargin
    )
  }

  def testOverrideNoReturnType(): Unit = {
    setCompilerOptions("-Xsource:3", "-Xmigration")

    runCompilerDiagnosticsTest(
      fileName = "overrideNoReturnType.scala",
      content =
        """
          |trait T { def f: Object }
          |class K extends T { val f = "" }
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(47, 57)),
          quickFixDescriptions = Seq("Add explicit type"),
          msgPrefix = "under -Xsource:3, inferred Object instead of String [quickfixable]"
        )
      ),
      expectedContent =
        """
          |trait T { def f: Object }
          |class K extends T { val f: String = "" }
          |""".stripMargin
    )
  }

  def testKeywordIdentifiers(): Unit = {
    setCompilerOptions("-Xlint")

    runCompilerDiagnosticsTest(
      fileName = "KeywordIdentifiers.scala",
      content =
        """
          |object KeywordIdentifiers {
          |  val then = 1
          |  val export = 2
          |  val enum = 3
          |}
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(35, 39)),
          quickFixDescriptions = Seq("Add backticks"),
          msgPrefix = "Wrap `then` in backticks to use it as an identifier, it will become a keyword in Scala 3. [quickfixable"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(50, 56)),
          quickFixDescriptions = Seq("Add backticks"),
          msgPrefix = "Wrap `export` in backticks to use it as an identifier, it will become a keyword in Scala 3. [quickfixable]"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(67, 71)),
          quickFixDescriptions = Seq("Add backticks"),
          msgPrefix = "Wrap `enum` in backticks to use it as an identifier, it will become a keyword in Scala 3. [quickfixable]"
        )
      ),
      expectedContent =
        """
          |object KeywordIdentifiers {
          |  val `then` = 1
          |  val `export` = 2
          |  val `enum` = 3
          |}
          |""".stripMargin
    )
  }

  def testPrimitiveWidening(): Unit = {
    setCompilerOptions("-Xlint")

    runCompilerDiagnosticsTest(
      fileName = "PrimitiveWidening.scala",
      content =
        """
          |object PrimitiveWidening {
          |  val test: Float = 123.abs
          |}
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(48, 55)),
          quickFixDescriptions = Seq("Add conversion"),
          msgPrefix = "Widening conversion from Int to Float is deprecated because it loses precision. Write `.toFloat` instead. [quickfixable]"
        )
      ),
      expectedContent =
        """
          |object PrimitiveWidening {
          |  val test: Float = 123.abs.toFloat
          |}
          |""".stripMargin
    )
  }

  def testProcedureSyntax(): Unit = {
    setCompilerOptions("-Xlint")

    runCompilerDiagnosticsTest(
      fileName = "ProcedureSyntax.scala",
      content =
        """
          |object ProcedureSyntax {
          |  def foo() { println() }
          |
          |  def bar: Unit = {}
          |}
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(38, 39)),
          quickFixDescriptions = Seq("Replace procedure syntax"),
          msgPrefix = "procedure syntax is deprecated: instead, add `: Unit =` to explicitly declare `foo`'s return type [quickfixable]"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(59, 62)),
          quickFixDescriptions = Seq("Add empty parameter list"),
          msgPrefix = "side-effecting nullary methods are discouraged: suggest defining as `def bar()` instead [quickfixable]"
        )
      ),
      expectedContent =
        """
          |object ProcedureSyntax {
          |  def foo(): Unit = { println() }
          |
          |  def bar(): Unit = {}
          |}
          |""".stripMargin
    )
  }
}
