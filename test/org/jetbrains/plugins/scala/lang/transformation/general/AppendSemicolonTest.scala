package org.jetbrains.plugins.scala.lang.transformation.general

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class AppendSemicolonTest extends TransformerTest(new AppendSemicolon()) {
  def testSingleLineSeparator() = check(
    "A\nB",
    "A;\nB;"
  )

  def testMultipleLineSeparators() = check(
    "A\n\nB",
    "A;\n\nB;"
  )

  def testExplicit() = check(
    "A;\nB;",
    "A;\nB;"
  )
}
