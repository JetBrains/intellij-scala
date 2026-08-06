package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

trait LineCommentsTestOps { self: AbstractScalaFormatterTestBase =>

  /**
   * This test method ensures that formatting doesn't change even if there are line comments on each line.
   * It's needed because in some cases handling of line comments is quite tricky
   */
  def doTextTestWithLineComments(before: String, after: String): Unit = {
    doTextTest(before, after)
    doTextTest(appendLineComments(before), appendLineComments(after))
    doTextTest(appendLineComments(before, withSpace = false), appendLineComments(after))
  }

  def doTextTestWithLineComments(before: String): Unit =
    doTextTestWithLineComments(before, before)

  // Append line comment on each non-empty line
  private def appendLineComments(text: String, withSpace: Boolean = true): String = {
    text.linesIterator
      .map { line =>
        if (line.trim.isEmpty) line
        else line + (if (withSpace) " " else "")  +"//comment"
      }
      .mkString("\n")
  }
}
