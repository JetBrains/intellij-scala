package org.jetbrains.plugins.scala.annotator

import fastparse.all._
import junit.framework.TestCase
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Group, Match, Mismatch}
import org.junit.Assert.assertEquals

class TypeDiffFlattenTest extends TestCase {
  private val element = P(CharPred(_.isLetterOrDigit).rep(1)).!.map(Match(_))
  private val comma = P(", ".rep(0))
  private val group = P("(" ~ parser.rep(0) ~ ")").map(Group(_: _*))
  private val parser: Parser[TypeDiff] = P((group | element) ~ comma)

  def testFlatten(): Unit = {
    assertFlattenedTo(100, "", "")
    assertFlattenedTo(100, "foo", "foo")
    assertFlattenedTo(100, "foo, bar", "foo, bar")

    assertFlattenedTo(100, "()", "")
    assertFlattenedTo(100, "(foo)", "foo")
    assertFlattenedTo(100, "(foo, bar)", "foo, bar")
    assertFlattenedTo(100, "foo, (bar)", "foo, bar")
    assertFlattenedTo(100, "(foo), bar", "foo, bar")
    assertFlattenedTo(100, "(foo), (bar)", "foo, bar")

    assertFlattenedTo(100, "(())", "")
    assertFlattenedTo(100, "((foo))", "foo")
    assertFlattenedTo(100, "((foo, bar))", "foo, bar")
    assertFlattenedTo(100, "(foo, (bar))", "foo, bar")
    assertFlattenedTo(100, "((foo), bar)", "foo, bar")
    assertFlattenedTo(100, "((foo), (bar))", "foo, bar")
  }

  // The root is implicitly added by the assertFlattenedTo method
  def testMaxChars(): Unit = {
    assertFlattenedTo(0, "", "")
    assertFlattenedTo(0, "foo", "(foo)")
    assertFlattenedTo(0, "foo, bar", "(foo, bar)")

    assertFlattenedTo(0, "()", "")
    assertFlattenedTo(0, "(foo)", "(foo)")
    assertFlattenedTo(0, "foo, (bar)", "(foo, (bar))")
    assertFlattenedTo(0, "(foo), bar", "((foo), bar)")
    assertFlattenedTo(0, "(foo), (bar)", "(foo), (bar)")

    assertFlattenedTo(3, "foo, (bar)", "foo, (bar)")
    assertFlattenedTo(3, "(foo), bar", "(foo), bar")
    assertFlattenedTo(3, "(foo), (bar)", "foo, (bar)")

    assertFlattenedTo(0, "(foo), (bar), (moo)", "(foo), (bar), (moo)")
    assertFlattenedTo(3, "(foo), (bar), (moo)", "foo, (bar), (moo)")
    assertFlattenedTo(6, "(foo), (bar), (moo)", "foo, bar, (moo)")
    assertFlattenedTo(9, "(foo), (bar), (moo)", "foo, bar, moo")

    assertFlattenedTo(0, "foo, (bar, (moo))", "(foo, (bar, (moo)))")
    assertFlattenedTo(3, "foo, (bar, (moo))", "foo, (bar, (moo))")
    assertFlattenedTo(6, "foo, (bar, (moo))", "foo, bar, (moo)")
    assertFlattenedTo(9, "foo, (bar, (moo))", "foo, bar, moo")

    assertFlattenedTo(0, "((foo), bar), moo", "(((foo), bar), moo)")
    assertFlattenedTo(3, "((foo), bar), moo", "((foo), bar), moo")
    assertFlattenedTo(6, "((foo), bar), moo", "(foo), bar, moo")
    assertFlattenedTo(9, "((foo), bar), moo", "foo, bar, moo")
  }

  def testGroupLength(): Unit = {
    assertFlattenedTo(0, "foo", "foo", 6)
    assertFlattenedTo(0, "foo", "foo", 3)

    assertFlattenedTo(3, "foo, (bar)", "(foo, (bar))", 1)
  }

  private def assertFlattenedTo(maxChars: Int, elements: String, expectedElements: String, groupLength: Int = 0): Unit = {
    val result = group.parse("(" + elements + ")").get.value.flattenTo(maxChars, groupLength)
    assertEquals(expectedElements, result.map(asString).mkString(", "))
  }

  private def asString(diff: TypeDiff): String = diff match {
    case Group(elements @_*) => s"(${elements.map(asString).mkString(", ")})"
    case Match(text, _) => text
    case Mismatch(text, _) => s"~$text~"
  }
}
