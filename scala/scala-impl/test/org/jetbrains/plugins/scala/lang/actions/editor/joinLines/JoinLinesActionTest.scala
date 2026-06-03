package org.jetbrains.plugins.scala.lang.actions.editor.joinLines

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.{EditorActionHandler, EditorActionManager}
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase

import java.nio.file.Path

class JoinLinesActionTest extends AbstractActionTestBase {
  override protected def relativeTestDataPath: Path = Path.of("actions", "editor", "joinLines")

  override protected def createHandler: EditorActionHandler =
    EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_JOIN_LINES)
}
