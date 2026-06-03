package org.jetbrains.plugins.scala.lang.actions.editor

class AutoIndentReferenceAfterDotOnPrevLineTest extends EditorTypeActionTestBase {

  override protected def typedChar: Char = 'f'

  def testIndent(): Unit =
    doTest(
      s"""myObject.
         |$CARET""".stripMargin,
      s"""myObject.
         |  f$CARET""".stripMargin
    )
}
