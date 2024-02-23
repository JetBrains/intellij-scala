package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion

class CompilerDiagnosticsTest_3 extends CompilerDiagnosticsTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testConvertToFunctionValue(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "ConvertToFunctionValue.scala",
      content =
        """
          |object ConvertToFunctionValue:
          |  def x: Int = 3
          |  val test = x _
          |  val y = 2 * x
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(62, 65)),
          quickFixDescriptions = Seq("Rewrite to function value"),
          msgPrefix = "Only function types can be followed by _ but the current expression has type Int"
        )
      ),
      expectedContent =
        """
          |object ConvertToFunctionValue:
          |  def x: Int = 3
          |  val test = (() => x)
          |  val y = 2 * x
          |""".stripMargin
    )
  }

  def testInsertBracesForEmptyArgument(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "InsertBracesForEmptyArgument.scala",
      content =
        """
          |object InsertBracesForEmptyArgument:
          |  def foo(): Unit = ()
          |  val x = foo
          |  def bar(): Unit = println()
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(71, 74)),
          quickFixDescriptions = Seq("Insert ()"),
          msgPrefix = "method foo in object InsertBracesForEmptyArgument must be called with () argument"
        )
      ),
      expectedContent =
        """
          |object InsertBracesForEmptyArgument:
          |  def foo(): Unit = ()
          |  val x = foo()
          |  def bar(): Unit = println()
          |""".stripMargin
    )
  }

  def testRemoveRepeatModifier(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "RemoveRepeatModifier.scala",
      content =
        """
          |final final class RemoveRepeatModifier
          |
          |object RemoveRepeatModifier
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(7, 12)),
          quickFixDescriptions = Seq("""Remove repeated modifier: "final""""),
          msgPrefix = "Repeated modifier final"
        )
      ),
      expectedContent =
        """
          |final  class RemoveRepeatModifier
          |
          |object RemoveRepeatModifier
          |""".stripMargin // The double space is expected, the compiler doesn't remove the extra space character.
    )
  }

  def testInsertMissingCasesEnum(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "InsertMissingCasesEnum.scala",
      content =
        """
          |enum Tree:
          |  case Node(l: Tree, r: Tree)
          |  case Leaf(v: String)
          |
          |object InsertMissingCasesEnum:
          |  def foo(tree: Tree) = tree match
          |    case Tree.Node(_, _) => ???
          |
          |  def bar(): Unit = println()
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(121, 125)),
          quickFixDescriptions = Seq("Insert missing cases (1)"),
          msgPrefix = "match may not be exhaustive."
        )
      ),
      expectedContent =
        """
          |enum Tree:
          |  case Node(l: Tree, r: Tree)
          |  case Leaf(v: String)
          |
          |object InsertMissingCasesEnum:
          |  def foo(tree: Tree) = tree match
          |    case Tree.Node(_, _) => ???
          |    case Tree.Leaf(_) => ???
          |
          |  def bar(): Unit = println()
          |""".stripMargin
    )
  }

  def testInsertMissingCasesForUnionStringType(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "InsertMissingCasesForUnionStringType.scala",
      content =
        """
          |object InsertMissingCasesForUnionStringType:
          |  def foo(text: "Alice" | "Bob" | "Charlie") = text match
          |    case "Bob" => ???
          |  end foo
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(93, 97)),
          quickFixDescriptions = Seq("Insert missing cases (2)"),
          msgPrefix = "match may not be exhaustive."
        )
      ),
      expectedContent =
        """
          |object InsertMissingCasesForUnionStringType:
          |  def foo(text: "Alice" | "Bob" | "Charlie") = text match
          |    case "Bob" => ???
          |    case "Alice" => ???
          |    case "Charlie" => ???
          |  end foo
          |""".stripMargin
    )
  }

  def testInsertMissingCasesForUnionIntType(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "InsertMissingCasesForUnionIntType.scala",
      content =
        """
          |object InsertMissingCasesForUnionIntType:
          |  def foo(num: 1 | 2 | 3 | 4) = num match
          |    case 2 => ???
          |    case 4 => ???
          |  end foo
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(75, 78)),
          quickFixDescriptions = Seq("Insert missing cases (2)"),
          msgPrefix = "match may not be exhaustive."
        )
      ),
      expectedContent =
        """
          |object InsertMissingCasesForUnionIntType:
          |  def foo(num: 1 | 2 | 3 | 4) = num match
          |    case 2 => ???
          |    case 4 => ???
          |    case 1 => ???
          |    case 3 => ???
          |  end foo
          |""".stripMargin
    )
  }

  def testInsertMissingCasesWithBraces(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "InsertMissingCasesWithBraces.scala",
      content =
        """
          |object InsertMissingCasesWithBraces:
          |  def foo(num: 1 | 2 | 3 | 4 | 5) = num match {
          |    case 5 => ???
          |    case 3 => ???
          |  }
          |end InsertMissingCasesWithBraces
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.WARNING,
          range = Some(TextRange.create(74, 77)),
          quickFixDescriptions = Seq("Insert missing cases (3)"),
          msgPrefix = "match may not be exhaustive."
        )
      ),
      expectedContent =
        """
          |object InsertMissingCasesWithBraces:
          |  def foo(num: 1 | 2 | 3 | 4 | 5) = num match {
          |    case 5 => ???
          |    case 3 => ???
          |    case 1 => ???
          |    case 2 => ???
          |    case 4 => ???
          |  }
          |end InsertMissingCasesWithBraces
          |""".stripMargin
    )
  }

  def testIntersectingRangesAfterApplication(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "IntersectingRangesAfterApplication.scala",
      content =
        """
          |private final private final class IntersectingRangesAfterApplication
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(15, 22)),
          quickFixDescriptions = Seq("""Remove repeated modifier: "private""""),
          msgPrefix = "Repeated modifier private"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(23, 28)),
          quickFixDescriptions = Seq("""Remove repeated modifier: "final""""),
          msgPrefix = "Repeated modifier final"
        )
      ),
      expectedContent =
        """
          |private final   class IntersectingRangesAfterApplication
          |""".stripMargin
    )
  }
}
