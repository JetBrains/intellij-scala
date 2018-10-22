package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.actions.implicitArguments.ShowImplicitArgumentsAction
import org.jetbrains.plugins.scala.codeInsight.implicits.{Hint, MouseHandler}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner

class ImplicitArgumentsPopup extends AnAction("Implicit Arguments Popup") {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    Hint.elementOf(inlay) match {
      case ImplicitArgumentsOwner(args) =>
        ShowImplicitArgumentsAction.showPopup(editor, args, isConversion = false)
      case _ =>
    }
  }
}
