package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.codeInsight.implicits.{Hint, MouseHandler}
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction

class RemoveExplicitArguments extends AnAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    val element = Hint.elementOf(inlay)

    inWriteCommandAction(editor.getProject)(element.getParent.replace(element.getPrevSibling))
    inlay.dispose()
  }
}
