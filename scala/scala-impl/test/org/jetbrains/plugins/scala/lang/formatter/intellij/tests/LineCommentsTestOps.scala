package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

trait LineCommentsTestOps { self: AbstractScalaFormatterTestBase =>

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
