package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.implicits.{ImplicitHint, MouseHandler}
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction

class RemoveExplicitArguments extends AnAction(
  ScalaCodeInsightBundle.message("remove.explicit.arguments.action.text"),
  ScalaCodeInsightBundle.message("remove.explicit.arguments.action.description"),
  null
) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    val element = ImplicitHint.elementOf(inlay)

    inWriteCommandAction(element.getParent.replace(element.getPrevSibling))(editor.getProject)
    inlay.dispose()
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}
