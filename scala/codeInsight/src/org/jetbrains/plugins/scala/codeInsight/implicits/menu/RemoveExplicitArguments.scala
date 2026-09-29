package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.util.Disposer
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

    inWriteCommandAction(element.getParent.replace(element.getPrevSibling))(using editor.getProject)
    Disposer.dispose(inlay)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}
