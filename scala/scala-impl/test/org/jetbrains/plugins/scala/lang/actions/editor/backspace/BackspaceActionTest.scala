package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.{EditorActionHandler, EditorActionManager}
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase

class BackspaceActionTest extends TestCase with ScalaBackspaceHandlerTestLike

object BackspaceActionTest {
  def suite(): Test = new AbstractActionTestBase("/actions/editor/backspace/data") {
    override protected def getMyHandler: EditorActionHandler =
      EditorActionManager.getInstance.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE)
  }
}