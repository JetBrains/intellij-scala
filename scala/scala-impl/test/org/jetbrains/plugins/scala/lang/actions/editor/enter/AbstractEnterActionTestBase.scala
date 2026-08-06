package org.jetbrains.plugins.scala.lang.actions.editor.enter

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.{EditorActionHandler, EditorActionManager}
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase

abstract class AbstractEnterActionTestBase extends AbstractActionTestBase {
  override protected def createHandler: EditorActionHandler =
    EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)

  override protected def setUp(): Unit = {
    super.setUp()
    commonCodeStyleSettings.INDENT_CASE_FROM_SWITCH = true
  }
}
