package org.jetbrains.plugins.scala.annotator

import fastparse.all._
import junit.framework.TestCase
import org.jetbrains.plugins.scala.annotator.Tree._
import org.jetbrains.plugins.scala.annotator.TypeDiff._
import org.junit.Assert.assertEquals

class TreeTest extends TestCase {
  private val element = P(CharPred(_.isLetterOrDigit).rep(1)).!.map(s => Leaf(Match(s)))
  private val comma = P(", ".rep(0))
  private val group = P("(" ~ parser.rep(0) ~ ")").map(Node(_))
  private val parser: Parser[Tree[Diff]] = P((group | element) ~ comma)

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
    val result = group.parse("(" + elements + ")").get.value.flattenTo(lengthOf(groupLength), maxChars)
    assertEquals(expectedElements, result.map(asString).mkString(", "))
  }

  private def asString(diff: Tree[Diff]): String = diff match {
    case Node(elements) => s"(${elements.map(asString).mkString(", ")})"
    case Leaf(Match(text, _)) => text
    case Leaf(Mismatch(text, _)) => s"~$text~"
  }

  // TODO unify
  private def lengthOf(nodeLength: Int)(tree: Tree[Diff]) = tree match {
    case Node(_) => nodeLength
    case Leaf(Match(text, _)) => text.length
    case Leaf(Mismatch(text, _)) => text.length
  }
}
