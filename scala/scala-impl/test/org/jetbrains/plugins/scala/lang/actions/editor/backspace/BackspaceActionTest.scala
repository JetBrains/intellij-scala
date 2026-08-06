package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.{EditorActionHandler, EditorActionManager}
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase

import java.nio.file.Path

class BackspaceActionTest extends AbstractActionTestBase with ScalaBackspaceHandlerTestLike {
  override protected def relativeTestDataPath: Path = Path.of("actions", "editor", "backspace", "data")

  override protected def createHandler: EditorActionHandler =
    EditorActionManager.getInstance.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE)
}
