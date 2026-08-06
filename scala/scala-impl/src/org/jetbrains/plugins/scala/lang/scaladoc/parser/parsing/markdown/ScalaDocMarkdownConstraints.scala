package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.constraints.{CommonMarkdownConstraints, MarkdownConstraints}

/*
We need to override the constraints of CommonMark because tags modify the constraints
(essentially they immediately terminate any previous blocks, and kill any constraints, which is hard to express)

Unfortunately, `CommonMarkdownConstraints` is relatively complex, *and* assumes that there are only ever 2 kinds of
constraints; lists, and block quotes.

Tags aren't *quite* like either, which is very inconvenient. They don't generate constraints after their line of creation,
and they cannot be terminated by anything other than another tag.

This means we need to understand the internal details of:
- CommonMarkdownConstraints
- MarkdownConstraints
- MarkerProcessor
- MarkerProcessorFactory

And know how they all deal with these blocks.

The solution I came up with is to add `overridesTag`, which is an ephemeral flag indicating whether the current line
started a new tag, in which case all previous blocks are killed.

We override `startsWith` and cheat a little. See its note for more.
 */
class ScalaDocMarkdownConstraints(indents: Array[Int], types: Array[Char], isExplicit: Array[Boolean], charsEaten: Int, val overridesTag : Boolean) extends CommonMarkdownConstraints(indents, types, isExplicit, charsEaten) {
  override def getBase: CommonMarkdownConstraints = ScalaDocMarkdownConstraints.BASE

  override def createNewConstraints(indents: Array[Int], types: Array[Char], isExplicit: Array[Boolean], charsEaten: Int): CommonMarkdownConstraints = {
    // When creating new constraints, we're either:
    // 1. Basing ourselves off of BASE, which doesn't have an overridesTag
    // 2. Modifying the current constraints, in which case we need to keep the overridesTag.
    new ScalaDocMarkdownConstraints(indents, types, isExplicit, charsEaten, this.overridesTag)
  }

  override def startsWith(other: MarkdownConstraints): Boolean = {
    if (overridesTag && other.getTypes.nonEmpty) false
    else super.startsWith(other)
  }

  override def applyToNextLine(pos: LookaheadText#Position): CommonMarkdownConstraints = {
    // If there's a tag, all constraints get overridden, so we go back to base constraints for the line
    // Plus the marker that there's an override
    if (ScalaDocMarkdownFlavour.getTagOnLine(pos).isDefined)
      ScalaDocMarkdownConstraints.BASE
    else super.applyToNextLine(pos)
  }

  override def addModifierIfNeeded(pos: LookaheadText#Position): CommonMarkdownConstraints =
    Option(super.addModifierIfNeeded(pos)).getOrElse(tryAddTag(pos))

  private def tryAddTag(pos: LookaheadText#Position): ScalaDocMarkdownConstraints = {
    // Tags need to be the first modifier of the line.
    if (pos == null || pos.getOffsetInCurrentLine != 0) return null

    ScalaDocMarkdownFlavour.getTagOnLine(pos) match {
      case Some(tag) =>
        new ScalaDocMarkdownConstraints(Array(), Array(), Array(), tag.bodyStart, true)
      case None => null
    }
  }
}
object ScalaDocMarkdownConstraints {
  val BASE = new ScalaDocMarkdownConstraints(Array(), Array(), Array(), 0, false)
}