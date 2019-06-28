package org.jetbrains.plugins.scala
package lang.actions.editor.backspace

class StringLiteralBackspaceActionTest extends ScalaBackspaceHandlerBaseTest {

  def testSimpleMultiLine(): Unit = doTest(
    s"val x = ${"\"\"\""}$CARET${"\"\"\""}",
    s"val x = ${"\"\""}$CARET"
  )

  def testInterpolated(): Unit = doTest(
    s"val x = s${"\"\"\""}$CARET${"\"\"\""}",
    s"val x = s${"\"\""}$CARET"
  )

  def testSimpleInterpolated(): Unit = doTest(
    s"""val x = s"$CARET"""",
    s"val x = s$CARET"
  )

}
