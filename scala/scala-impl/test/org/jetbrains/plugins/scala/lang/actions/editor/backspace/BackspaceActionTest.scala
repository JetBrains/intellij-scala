package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.{EditorActionHandler, EditorActionManager}
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class BackspaceActionTest
  extends AbstractActionTestBase("/actions/editor/backspace/data")
    with ScalaBackspaceHandlerTestLike {

  override protected def getMyHandler: EditorActionHandler =
    EditorActionManager.getInstance.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE)
}

object BackspaceActionTest {
  def suite = new BackspaceActionTest
}