package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown

import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints

class ScalaDocMarkdownConstraints(indents: Array[Int], types: Array[Char], isExplicit: Array[Boolean], charsEaten: Int) extends CommonMarkdownConstraints(indents, types, isExplicit, charsEaten) {
  override def applyToNextLine(pos: LookaheadText#Position): CommonMarkdownConstraints = {
    if (pos == null) {
      return super.applyToNextLine(pos)
    }

    val offset = pos.charsToNonWhitespace()
    val line = pos.getCurrentLine

    if (offset < line.length() && line.charAt(offset) == '@') {
      // We're in a tag, we override!
      var spacePos = offset + 1
      while (spacePos < line.length() && !Character.isWhitespace(line.charAt(spacePos))) {
        spacePos += 1
      }
      new ScalaDocMarkdownConstraints(Array(), Array(), Array(), spacePos)
    } else {
      super.applyToNextLine(pos)
    }
  }
}
object ScalaDocMarkdownConstraints {
  val BASE = new ScalaDocMarkdownConstraints(Array(), Array(), Array(), 0)
}