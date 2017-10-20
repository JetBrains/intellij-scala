package org.jetbrains.plugins.scala
package lang.actions.editor.backspace

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * User: Dmitry.Naydanov
 * Date: 31.07.14.
 */
class StringLiteralBackspaceActionTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  def testSimpleMultiLine(): Unit = {
    checkGeneratedTextAfterBackspace(s"val x = ${"\"\"\""}$CARET_MARKER${"\"\"\""}",
      s"val x = ${"\"\""}$CARET_MARKER")
  }

  def testInterpolated(): Unit = {
    checkGeneratedTextAfterBackspace(s"val x = s${"\"\"\""}$CARET_MARKER${"\"\"\""}",
      s"val x = s${"\"\""}$CARET_MARKER")
  }

  def testSimpleInterpolated(): Unit = {
    checkGeneratedTextAfterBackspace(
      s"""val x = s"$CARET_MARKER"""",
      s"val x = s$CARET_MARKER")
  }
}
