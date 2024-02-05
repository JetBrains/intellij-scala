package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.actions.implicitArguments.ShowImplicitArgumentsAction
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.implicits.{ImplicitHint, MouseHandler}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner

class ImplicitArgumentsPopup extends AnAction(
  ScalaCodeInsightBundle.message("implicit.arguments.popup.action.text"),
  ScalaCodeInsightBundle.message("implicit.arguments.popup.action.description"),
  null
) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    if (editor == null) return
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    if (inlay == null) return

    ImplicitHint.elementOf(inlay) match {
      case ImplicitArgumentsOwner(args) =>
        ShowImplicitArgumentsAction.showPopup(editor, args, isConversion = false)
      case _ =>
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}
