package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{ActionManager, AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.codeInsight.implicits.{Hint, MouseHandler}

class ImplicitArgumentsPopup extends AnAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    val element = Hint.elementOf(inlay)

    val range = element.getTextRange
    editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)

    val action = ActionManager.getInstance.getAction("Scala.ShowImplicitArguments")
    action.actionPerformed(e)
  }
}
