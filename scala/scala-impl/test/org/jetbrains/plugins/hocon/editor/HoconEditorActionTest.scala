package org.jetbrains.plugins.hocon
package editor

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.testFramework.EditorTestUtil
import org.junit.Assert.assertNotNull

abstract class HoconEditorActionTest protected(override protected val actionId: String,
                                               subPath: String) extends HoconActionTest(actionId, subPath) {

  import HoconFileSetTestCase._

  override protected def executeAction(dataContext: DataContext)
                                      (implicit editor: Editor): String = {
    val actionHandler = EditorActionManager.getInstance.getActionHandler(actionId)
    assertNotNull(actionHandler)

    val caretModel = editor.getCaretModel
    inWriteCommandAction {
      actionHandler.execute(editor, caretModel.getCurrentCaret, dataContext)
    }

    val fileText = editor.getDocument.getText

    caretModel.getOffset match {
      case offset if (0 to fileText.length).contains(offset) =>
        fileText.substring(0, offset) + EditorTestUtil.CARET_TAG + fileText.substring(offset)
      case _ => fileText
    }
  }
}
