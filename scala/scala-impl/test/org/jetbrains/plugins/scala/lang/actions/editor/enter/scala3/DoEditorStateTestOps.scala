package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.base.EditorActionTestBase

trait DoEditorStateTestOps extends CheckIndentAfterTypingCodeOps {
  self: EditorActionTestBase =>

  protected def doEditorStateTest(states: (String, TypeText)*): Unit =
    doEditorStateTest(EditorStates(states: _*))

  protected def doEditorStateTest(editorStates: EditorStates): Unit = {
    val states = editorStates.states
    states.sliding(2).foreach { case Seq(before, after) =>
      val textBefore = before.text
      val textToType = before.textToType
      val textAfter = after.text

      performTestWithConvenientCaretsDiffView(textBefore, textAfter, stripTrailingSpaces = true) { () =>
        val lines = linesToType(textToType)
        for {
          (line, lineIdx) <- lines.zipWithIndex
        } {
          if (lineIdx > 0) {
            performEnterAction()
          }
          if (line.nonEmpty) {
            performTypingAction(line)
            if (StringUtils.isNotBlank(line)) {
              adjustLineIndentAtCaretPosition()
            }
          }
        }
      }
    }
  }
}
