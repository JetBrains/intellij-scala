package org.jetbrains.plugins.scala
package lang.actions.editor.backspace

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry.Naydanov
 * Date: 31.07.14.
 */
class StringLiteralBackspaceActionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSimpleMultiLine() {
    checkGeneratedTextAfterBackspace(s"val x = ${"\"\"\""}$CARET_MARKER${"\"\"\""}", s"val x = ${"\"\""}$CARET_MARKER")
  }
  
  def testInterpolated() {
    checkGeneratedTextAfterBackspace(s"val x = s${"\"\"\""}$CARET_MARKER${"\"\"\""}", s"val x = s${"\"\""}$CARET_MARKER")
  }

  def testSimpleInterpolated() {
    checkGeneratedTextAfterBackspace(s"""val x = s"$CARET_MARKER"""", s"val x = s$CARET_MARKER")
  }
}
