package org.jetbrains.plugins.scala
package annotator

import junit.framework.TestCase
import org.jetbrains.plugins.scala.annotator.Tree._
import org.jetbrains.plugins.scala.annotator.TypeDiff._
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class TreeTest extends TestCase {
  import scala.util.parsing.combinator._

  private object Language extends RegexParsers {
    def comma: Parser[Unit]             = """\s*,\s*""".r                         ^^ { _ => () }
    def element: Parser[Leaf[TypeDiff]] = """\w+""".r                             ^^ { name => Leaf(Match(name))}
    def group: Parser[Node[TypeDiff]]   = ("(" ~> repsep(parser, comma) <~ ")")   ^^ { Node(_: _*) }
    def parser: Parser[Tree[TypeDiff]]  = group | element

    def apply(s: String): Node[TypeDiff] = parseAll(group, s"($s)").get
  }

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

  private def assertFlattenedTo(maxChars: Int, elements: String, expectedElements: String, nodeLength: Int = 0): Unit = {
    val result = Language(elements).flattenTo(lengthOf(nodeLength), maxChars)
//    val result = group.parse("(" + elements + ")").get.value.flattenTo(lengthOf(nodeLength), maxChars)
    assertEquals(expectedElements, result.map(asString).mkString(", "))
  }

  private def asString(diff: Tree[TypeDiff]): String = diff match {
    case Node(elements @_*) => s"(${elements.map(asString).mkString(", ")})"
    case Leaf(Match(text, _)) => text
    case Leaf(Mismatch(text, _)) => s"~$text~"
  }
}
