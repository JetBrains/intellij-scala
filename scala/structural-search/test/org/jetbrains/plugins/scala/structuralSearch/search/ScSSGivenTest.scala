package org.jetbrains.plugins.scala.structuralSearch.search

import com.intellij.structuralsearch.MatchOptions
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSGivenTest extends ScalaStructuralSearchTestCase {

  private def setMinMax(name: String, min: Int, max: Int)(opt: MatchOptions): Unit = {
    val v = opt.addNewVariableConstraint(name)
    v.setMinCount(min)
    v.setMaxCount(max)
  }

  def testBasicGiven(): Unit = {
    val content =
      """
        |<match="Decl">given Type</match="Decl">
        |
        |<match="Alias">given Type = expr</match="Alias">
        |
        |<match="Def">given Type with {
        |  def foo = expr
        |}</match="Def">
        |""".stripMargin

    matchAndAssert(
      "Match Incomplete",
      content,
      "given"
    )

    matchAndAssert(
      "Match Alias Decl",
      content,
      "given $type$"
    )

    matchAndAssert(
      "Match By Type",
      content,
      "given Type"
    )

    matchAndAssert(
      "Match By Type",
      clearMarker(content),
      "given NoType"
    )

    matchAndAssert(
      "Match Alias Def",
      clearMarker(content, Set("Alias")),
      "given $type$ = $expr$",
      setMinMax("expr", 1, 1)
    )

    matchAndAssert(
      "Match Alias Def",
      clearMarker(content, Set("Alias", "Decl")),
      "given $type$ = $expr$",
      setMinMax("expr", 0, 1)
    )

    matchAndAssert(
      "Match Alias Def",
      clearMarker(content, Set("Decl")),
      "given $type$ = $expr$",
      setMinMax("expr", 0, 0)
    )

    matchAndAssert(
      "Match Def with empty body",
      clearMarker(content, Set("Def")),
      "given $type$ with { }"
    )

    matchAndAssert(
      "Match Def with body",
      clearMarker(content, Set("Def")),
      "given $type$ with { def foo }"
    )

    matchAndAssert(
      "Don't match Def with different body",
      clearMarker(content),
      "given $type$ with { def bar }"
    )
  }

  def testName(): Unit = {
    val content =
      """
        |<match="Named">given name: Named = ???</match="Named">
        |
        |<match="Unnamed">given Unnamed = ???</match="Unnamed">
        |""".stripMargin

    matchAndAssert(
      "Match Incomplete",
      content,
      "given"
    )

    matchAndAssert(
      "Match specific name",
      clearMarker(content, Set("Named")),
      "given name: $type$",
      setMinMax("name", 1, 1)
    )

    matchAndAssert(
      "Match any name",
      content,
      "given $name$: $type$",
      setMinMax("name", 0, 1)
    )

    matchAndAssert(
      "Match any name",
      clearMarker(content, Set("Unnamed")),
      "given $name$: $type$",
      setMinMax("name", 0, 0)
    )
  }

  def testTypeParameters(): Unit = {
    val content =
      """
        |<match="Zero">given Type = ???</match="Zero">
        |
        |<match="One">given [T]: Type = ???</match="One">
        |
        |<match="Two">given [T, S]: Type = ???</match="Two">
        |""".stripMargin

    matchAndAssert(
      "Match Incomplete",
      content,
      "given"
    )

    matchAndAssert(
      "Match one",
      clearMarker(content, Set("One", "Two")),
      "given [$x$]"
    )

    matchAndAssert(
      "Match two explicitly",
      clearMarker(content, Set("Two")),
      "given [$x$, $y$]"
    )

    matchAndAssert(
      "Match zero by modifier",
      clearMarker(content, Set("Zero")),
      "given [$tp$]",
      setMinMax("tp", 0, 0)
    )

    matchAndAssert(
      "Match one two or three by modifier",
      clearMarker(content, Set("Zero", "One", "Two")),
      "given [$tp$]",
      setMinMax("tp", 0, 2)
    )

    matchAndAssert(
      "Match two by modifier",
      clearMarker(content, Set("Two")),
      "given [$tp$]",
      setMinMax("tp", 2, 2)
    )
  }

  def testParameters(): Unit = {
    val content =
      """
        |<match="Zero">given Type = ???</match="Zero">
        |
        |<match="One">given (p: T): Type = ???</match="One">
        |
        |<match="Two">given (p1: T, p2: T): Type = ???</match="Two">
        |""".stripMargin

    matchAndAssert(
      "Match Incomplete",
      content,
      "given"
    )

    matchAndAssert(
      "Match one",
      clearMarker(content, Set("One")),
      "given ($x$): $type$"
    )

    matchAndAssert(
      "Match two explicitly",
      clearMarker(content, Set("Two")),
      "given ($x$, $y$): $type$"
    )

    matchAndAssert(
      "Match zero by modifier",
      clearMarker(content, Set("Zero")),
      "given ($tp$): $type$",
      setMinMax("tp", 0, 0)
    )

    matchAndAssert(
      "Match one two or three by modifier",
      clearMarker(content, Set("Zero", "One", "Two")),
      "given ($tp$): $type$",
      setMinMax("tp", 0, 2)
    )

    matchAndAssert(
      "Match two by modifier",
      clearMarker(content, Set("Two")),
      "given ($tp$): $type$",
      setMinMax("tp", 2, 2)
    )
  }
}
